package com.example.productsadder

import android.app.Activity
import android.app.Notification.Action
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.productsadder.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.log

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var images = mutableListOf<Uri>()
    private var product: Product? = null
    private val productStorage = Firebase.storage.reference
    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        FirebaseApp.initializeApp(this)
        val selectImageActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val intent = result.data
                    if (intent?.clipData != null) {
                        //picked multiple images
                        //get number of picked images
                        val count = intent.clipData!!.itemCount
                        for (i in 0 until count) {
                            val imageUri = intent.clipData?.getItemAt(i)?.uri
                            //add image to list
                            imageUri?.let {
                                images.add(imageUri)
                            }

                        }
                        binding.tvSelectedImages.text = images.size.toString()

                    } else {
                        //picked single image
                        val imageUri = intent?.data
                        imageUri?.let {
                            images.add(imageUri)
                        }
                        binding.tvSelectedImages.text = images.size.toString()

                    }

                }
            }
        binding.buttonImagesPicker.setOnClickListener {

            val pickImg = Intent(Intent.ACTION_GET_CONTENT)
            pickImg.type = "image/*"
            pickImg.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            selectImageActivityResult.launch(pickImg)

        }

    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == R.id.saveProduct) {
            lifecycleScope.launch {
                saveProduct()
            }


        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun saveProduct() {
        if (checkInputProduct()) {
            val productName = binding.edName.text.toString().trim()
            val productCategory = binding.edCategory.text.toString().trim()
            val productDescription = binding.edDescription.text.toString().trim()
            val productPrice = binding.edPrice.text.toString()
            val productOfferPercentage = binding.offerPercentage.text.toString()
            val productColors: List<String> =
                binding.productColorET.text.toString().trim().split(",")
            val byteArrayImages: List<ByteArray> = getImagesByteArray()
            val productImages = mutableListOf<String>()


            lifecycleScope.launch(Dispatchers.IO) {
     withContext(Dispatchers.Main){
         showLoading()
     }

                try {
                    async {
                        byteArrayImages.forEach {
                            val id = UUID.randomUUID().toString()
                            launch {
                                val imageStorage = productStorage.child("Products/Images/$id")
                                val result = imageStorage.putBytes(it).await()
                                val downloadUrl = result.storage.downloadUrl.await().toString()
                                runBlocking {
                                    productImages.add(downloadUrl)

                                }
                            }
                        }
                    }.await()
                } catch (e: Exception) {
                    e.printStackTrace()
                        hideLoading()
                }
                product = Product(UUID.randomUUID().toString(),productName,productCategory,productPrice.toFloat(),
                    if (productOfferPercentage.isEmpty()) null else productOfferPercentage.toFloat(),
                    if (productDescription.isEmpty()) null else productDescription,
                    if(productColors.isEmpty()) null else productColors,
                    productImages)



                if (product != null) {
                    firestore.collection("Products").add(product!!).addOnSuccessListener {
                        hideLoading()
                        Log.e("Firestore","Product Successfully Uploaded")
                    }.addOnFailureListener {
                        hideLoading()
                        Log.e("Firestore"," Failed to Upload Product ")

                    }
                } else {
                    withContext(Dispatchers.Main){
                        Toast.makeText(this@MainActivity, " Sorry Product Doesn't Fit Requirements ", Toast.LENGTH_LONG)
                            .show()
                        hideLoading()
                    }

                }
                withContext(Dispatchers.Main){
                    emptyDataInputs()
                }

            }
        } else {
            withContext(Dispatchers.Main){
                Toast.makeText(this@MainActivity, " Please Make Sure To Input Data", Toast.LENGTH_LONG).show()

            }
        }
    }

    private fun emptyDataInputs() {
        binding.apply {
            edName.setText("")
            edPrice.setText("")
            edDescription.setText("")
            edCategory.setText("")
            productColorET.setText("")
            tvSelectedImages.text = ""
            images.clear()
        }
    }


    private fun getImagesByteArray(): List<ByteArray> {
        var imageBmp: Bitmap? = null
        val imageByteArray = mutableListOf<ByteArray>()
        images.forEach {
            val stream = ByteArrayOutputStream()
            imageBmp = getBitmap(contentResolver, it)
            /*   if(Build.VERSION.SDK_INT < 28) {
             imageBmp = MediaStore.Images.Media.getBitmap(contentResolver,it)
            }
            else{
                 val source = ImageDecoder.createSource(contentResolver,it)
                imageBmp = ImageDecoder.decodeBitmap(source)

            }*/
            if (imageBmp!!.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                imageByteArray.add(stream.toByteArray())
            }
        }
        return imageByteArray
    }


    private fun checkInputProduct(): Boolean {
        binding.apply {
            if (edName.text.toString().isEmpty())
                return false
            if (edPrice.text.toString().isEmpty())
                return false
            if (edCategory.text.toString().isEmpty())
                return false
            if (tvSelectedImages.text == "0")
                return false


            return true
        }
    }

    fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    fun hideLoading() {
        binding.progressBar.visibility = View.INVISIBLE
    }

    fun getBitmap(contentResolver: ContentResolver, fileUri: Uri?): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, fileUri!!))
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, fileUri)
            }
        } catch (e: Exception) {
            null
        }
    }
}

