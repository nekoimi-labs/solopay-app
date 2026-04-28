package cn.solopay.api

data class ApiResult(
    val httpStatus: Int,
    val businessCode: Int?,
    val message: String?,
    val rawBody: String,
) {
    val isUnauthorized: Boolean
        get() = httpStatus == 401 || (businessCode != null && businessCode in 40100..40199)
}
