package com.example.productsadder

import com.google.firebase.firestore.IgnoreExtraProperties

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Float,
    val offerPercentage: Float? = null,
    val description: String? = null,
    val colors: List<String>? = null,
    val images: List<String>
)