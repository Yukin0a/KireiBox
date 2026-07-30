package com.yuki.yukihub.net

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * 通用 REST API Retrofit 接口。
 *
 * 适用于 VNDB、Bangumi、月幕 Gal、AI Review 等 JSON API 场景。
 * 路径均使用 @Url 动态指定，由各 Client 拼接完整 URL。
 */
interface ApiService {

    @GET
    fun get(@Url url: String): Call<ResponseBody>

    @GET
    fun getWithHeader(@Url url: String, @Header("Authorization") auth: String): Call<ResponseBody>

    @POST
    fun post(@Url url: String, @Body body: RequestBody): Call<ResponseBody>

    @POST
    fun postWithAuth(
        @Url url: String,
        @Body body: RequestBody,
        @Header("Authorization") auth: String
    ): Call<ResponseBody>
}
