package com.jassemsendi.unpacked.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ResultTest {
    private val success: Result<Int, DataError.Local> = Result.Success(2)
    private val failure: Result<Int, DataError.Local> = Result.Error(DataError.Local.NOT_FOUND)

    @Test
    fun mapTransformsTheSuccessValueAndLeavesAnErrorAsItIs() {
        assertEquals(Result.Success("2"), success.map { it.toString() })
        assertEquals(Result.Error(DataError.Local.NOT_FOUND), failure.map { it.toString() })
    }

    @Test
    fun onSuccessAndOnFailureRunForTheirOwnCaseOnlyAndReturnTheResultForChaining() {
        val seen = mutableListOf<String>()

        val chained =
            success
                .onSuccess { seen += "success $it" }
                .onFailure { seen += "failure $it" }
        failure
            .onSuccess { seen += "success $it" }
            .onFailure { seen += "failure $it" }

        assertEquals(listOf("success 2", "failure NOT_FOUND"), seen)
        assertEquals(success, chained)
    }

    @Test
    fun asEmptyResultKeepsOnlyWhetherTheCallSucceeded() {
        assertEquals(Result.Success(Unit), success.asEmptyResult())
        assertEquals(Result.Error(DataError.Local.NOT_FOUND), failure.asEmptyResult())
    }
}
