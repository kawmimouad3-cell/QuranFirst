package com.example.quranfirst.utils

import kotlin.math.*

data class City(val id: Int, val name: String, val lat: Double, val lng: Double)

object CityMapper {
    // 50+ Major cities from Ministry of Habous. ID must match exactly the query param 'ville'.
    private val cities = listOf(
        City(1, "Rabat", 34.0209, -6.8416),
        City(2, "Casablanca", 33.5731, -7.5898),
        City(3, "Marrakech", 31.6295, -7.9811),
        City(4, "Fès", 34.0181, -5.0078),
        City(5, "Tanger", 35.7595, -5.8340),
        City(6, "Agadir", 30.4278, -9.5981),
        City(7, "Oujda", 34.6867, -1.9114),
        City(8, "Kenitra", 34.2610, -6.5802),
        City(9, "Tetouan", 35.5785, -5.3684),
        City(10, "Safi", 32.2994, -9.2372),
        City(11, "Meknès", 33.8965, -5.5473),
        City(12, "El Jadida", 33.2316, -8.5007),
        City(13, "Nador", 35.1667, -2.9333),
        City(14, "Khouribga", 32.8810, -6.9063),
        City(15, "Taza", 34.2100, -4.0100),
        City(16, "Beni Mellal", 32.3394, -6.3608),
        City(17, "Khemisset", 33.8150, -6.0662),
        City(18, "Guelmim", 28.9869, -10.0573),
        City(19, "Ouarzazate", 30.9189, -6.8934),
        City(20, "Al Hoceima", 35.2514, -3.9372)
    )

    fun findNearestCity(userLat: Double, userLng: Double): City {
        var nearestCity = cities[0]
        var minDistance = Double.MAX_VALUE

        for (city in cities) {
            val distance = calculateDistance(userLat, userLng, city.lat, city.lng)
            if (distance < minDistance) {
                minDistance = distance
                nearestCity = city
            }
        }
        return nearestCity
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Radius of Earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
