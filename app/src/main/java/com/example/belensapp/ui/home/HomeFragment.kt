package com.example.belensapp.ui.home

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.belensapp.R
import com.example.belensapp.api.ApiConfig
import com.example.belensapp.api.ApiService
import com.example.belensapp.api.NewsResponseItem
import com.example.belensapp.databinding.FragmentHomeBinding
import com.example.belensapp.ui.webview.WebViewActivity
import com.example.belensapp.utils.Constants
import com.google.firebase.database.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var databaseRef: DatabaseReference
    private lateinit var homeViewModel: HomeViewModel

    private lateinit var apiService: ApiService
    private lateinit var token: String
    private lateinit var newsAdapter: NewsAdapter


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        homeViewModel = ViewModelProvider(requireActivity()).get(HomeViewModel::class.java)
        apiService = ApiConfig.getApiService()

        val sharedPref = requireContext().getSharedPreferences(
            Constants.PREF_NAME,
            AppCompatActivity.MODE_PRIVATE
        )
        token = sharedPref.getString(Constants.KEY_USER_TOKEN, "") ?: ""


        initializeFirebase()
        setupRecyclerView()
        setupSearchView()


        observeLoadingState()
        if (homeViewModel.shouldRefreshData()) {
            fetchNews()
        }

        observeNewsData()
        updateThemeAppearance()
        loadProfileAndUsername()

        return root
    }

    private fun initializeFirebase() {
        val sharedPref = requireContext().getSharedPreferences(
            Constants.PREF_NAME,
            AppCompatActivity.MODE_PRIVATE
        )
        val userToken = sharedPref.getString(Constants.KEY_USER_TOKEN, null)
        userToken?.let {
            databaseRef =
                FirebaseDatabase.getInstance("https://belensapp-8eff1-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .getReference("user/$userToken")
        }
    }

    private fun observeLoadingState() {
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            if (!isLoading) {
                homeViewModel.showNoData.value?.let { showNoData ->
                    binding.recyclerView.visibility = if (showNoData) View.GONE else View.VISIBLE
                    binding.noDataText.visibility = if (showNoData) View.VISIBLE else View.GONE
                }
            }
        }

        homeViewModel.showNoData.observe(viewLifecycleOwner) { showNoData ->
            if (homeViewModel.isLoading.value == false) {
                binding.noDataText.visibility = if (showNoData) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (showNoData) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadProfileAndUsername() {
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _binding?.let { binding ->
                    if (snapshot.exists()) {
                        val username =
                            snapshot.child("username").getValue(String::class.java) ?: "User"
                        binding.welcomeText.text = getString(R.string.hai, username)
                        val photoUrl = snapshot.child("photoUrl").getValue(String::class.java)
                        if (!photoUrl.isNullOrEmpty()) {
                            Glide.with(requireContext()).load(photoUrl).circleCrop()
                                .into(binding.profileImage)
                        } else {
                            Glide.with(requireContext()).load(R.drawable.ic_launcher_foreground)
                                .circleCrop().into(binding.profileImage)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _binding?.welcomeText?.text = getString(R.string.hi_user)
            }
        })
    }




    private fun observeNewsData() {
        homeViewModel.newsList.observe(viewLifecycleOwner) { news ->
            if (news.isNotEmpty()) {
                newsAdapter = NewsAdapter(news) { newsItem ->
                    val intent = Intent(requireContext(), WebViewActivity::class.java)
                    intent.putExtra("url", newsItem.url)
                    intent.putExtra("title", newsItem.title)
                    intent.putExtra("imageUrl", newsItem.imageUrl)
                    startActivity(intent)
                }
                binding.recyclerView.adapter = newsAdapter
            }
        }
    }

    private fun fetchNews() {
        homeViewModel.setLoading(true)
        binding.noDataText.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE

        apiService.getNews("Bearer $token").enqueue(object : Callback<List<NewsResponseItem>> {
            override fun onResponse(
                call: Call<List<NewsResponseItem>>,
                response: Response<List<NewsResponseItem>>
            ) {
                if (response.isSuccessful) {
                    val newsList = response.body() ?: emptyList()
                    homeViewModel.setNews(newsList)
                } else {
                    homeViewModel.setLoading(false)
                    homeViewModel.setNews(emptyList())
                }
            }

            override fun onFailure(call: Call<List<NewsResponseItem>>, t: Throwable) {
                homeViewModel.setLoading(false)
                homeViewModel.setNews(emptyList())
            }
        })
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText.orEmpty()
                homeViewModel.filterNews(query)
                return true
            }
        })
    }


    private fun setupRecyclerView() {
        val recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateThemeAppearance()
    }

    private fun updateThemeAppearance() {
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> {
                binding.welcomeText.setTextColor(ContextCompat.getColor(requireContext(), R.color.nighttext))
                binding.infoTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.nighttext))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.searchView.setQuery("", false)
        binding.searchView.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}