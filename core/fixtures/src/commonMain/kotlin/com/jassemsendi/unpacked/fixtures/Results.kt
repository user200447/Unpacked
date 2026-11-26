package com.jassemsendi.unpacked.fixtures

import com.jassemsendi.unpacked.model.Result

/** The success value of a call a test expects to succeed; a typed error fails the test with its name. */
public fun <D> Result<D, *>.orFail(): D =
    when (this) {
        is Result.Success -> data
        is Result.Error -> throw AssertionError("expected a success but got $this")
    }
