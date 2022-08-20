package com.realityexpander.coroutinecancellationexception

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.realityexpander.coroutinecancellationexception.ui.theme.CoroutineCancellationExceptionTheme
import kotlinx.coroutines.*
import java.net.HttpRetryException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Example 1 (intentionally crashing)
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        // crashes here
                        throw Exception("Exception Example 1")
                    }
                } catch (e: Exception) {
                    println("Caught Exception: ${e.message}")
                }
            }
        }

        // Example 2 (inner coroutine caught and not propagated)
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        try {

                            // caught here
                            throw Exception("Exception Example 2")

                        } catch (e: Exception) {
                            println("Caught inner Exception: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    println("Caught outer Exception: ${e.message}")
                }
            }
        }

        // Example 3 (2 inner coroutines, exception propagated up one level)
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        launch {

                            // not handled & propagated up
                            // & not handled & propagated up to top coroutine
                            // & crashes here
                            throw Exception("Exception Example 3")
                        }
                    }
                } catch (e: Exception) {
                    println("Caught outer Exception: ${e.message}")
                }
            }
        }


        // Example 4 inner async coroutine, outer launch
        if (false) {
            lifecycleScope.launch {
                val string = async {
                    delay(500L)

                    // propagated up to top coroutine and not handled, crashes here
                    throw Exception("Exception Example 4")

                    "Result"
                }
                println("string: ${string.await()}")
            }
        }

        // Example 5 inner async coroutine, outer async, outer async never completes
        if (false) {
            val deferredString = lifecycleScope.async {
                val string = async {
                    delay(500L)

                    // propagated up to top coroutine and not handled,
                    // will *NOT* immediately crash because async coroutine is not yet complete.
                    throw Exception("Exception Example 5")

                    "Result"
                }
                println("string: ${string.await()}")

                string
            }
        }

        // Example 6 inner async coroutine, outer async, outer async completes & exception is not handled
        if (false) {
            val deferredString = lifecycleScope.async {
                val string = async {
                    delay(500L)

                    // propagated up to top coroutine and not handled,
                    // *will* crash here when async coroutine deferredString is .await()'ed.
                    throw Exception("Exception Example 6")

                    "Result"
                }
                println("string: ${string.await()}")

                string
            }

            // get the deferred string here, causes a crash due to unhandled exception
            lifecycleScope.launch {
                println("deferredString: ${deferredString.await()}")  // causes crash
            }
        }

        // Example 7 inner async coroutine, outer async, outer async completes & exception is handled
        if (false) {
            val deferredString = lifecycleScope.async {
                val string = async {
                    delay(500L)

                    // propagated up to top coroutine and not handled,
                    // *will* crash here when async coroutine deferredString is .await()'ed.
                    throw Exception("Exception Example 7")

                    "Result"
                }
                println("string: ${string.await()}")

                string
            }

            // get the deferred string here, no crash because exception is handled
            // WRONG WAY TO HANDLE THIS - DON'T DO THIS
            lifecycleScope.launch {
                try {
                    println("deferredString: ${deferredString.await()}")  // causes crash but handled
                } catch (e: Exception) {
                    println("Second coroutine Caught Exception: ${e.message}")
                }
            }
        }

        // Example 8 using exceptionHandler with inner async coroutine, outer async, outer async completes & exception is handled
        if (false) {
            // Must be installed into the ROOT coroutine
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("Caught Exception: ${throwable.message}")
            }

            lifecycleScope.launch(handler) {

                // Propagated up to top coroutine and handled by the exception handler (but not Cancellation exceptions)
                throw Exception("Exception Example 8")
            }
        }

        // Example 9 using CoroutineScopes w/o CoroutineExceptionHandler
        if (false) {
            CoroutineScope(Dispatchers.Main).launch {
                launch {
                    delay(500L)
                    throw Exception("Coroutine 1 failed - Example 9 ")  // crashes here
                }
                launch {
                    delay(600L)  // takes a little longer than "Coroutine 1"
                    println("Coroutine 2 finished - Example 9 ") // never completes
                }
            }
        }

        // Example 10 using CoroutineScopes w/ CoroutineExceptionHandler
        if (false) {
            // Must be installed into the ROOT coroutine
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("CoroutineExceptionHandler Caught Exception: ${throwable.message}")
            }

            // Because there is no SuperVisor scope, if one of the coroutines fails, the whole scope fails.
            CoroutineScope(Dispatchers.Main + handler).launch {
                launch {
                    delay(500L)

                    // propagates up to top coroutine and is handled by the exception handler
                    throw Exception("Coroutine 1 failed - Example 10 ")
                }
                launch {
                    delay(600L)  // takes a little longer than "Coroutine 1"
                    println("Coroutine 2 finished - Example 10 ") // never completes
                }
            }
        }


        // Example 11 using CoroutineScopes w/ CoroutineExceptionHandler + supervisorScope
        //   supervisorScope is used to allow the coroutines in the scope to run even if one of the coroutines fails.
        if (false) {
            // Must be installed into the ROOT coroutine
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("CoroutineExceptionHandler Caught Exception: ${throwable.message}")
            }

            CoroutineScope(Dispatchers.Main + handler).launch {
                supervisorScope {
                    launch {
                        delay(500L)

                        // propagates up to top coroutine and is handled by the exception handler
                        throw Exception("Coroutine 1 failed - Example 11 ")
                    }
                    launch {
                        delay(600L)  // takes a little longer than "Coroutine 1"
                        println("Coroutine 2 finished - Example 11 ") // completes now due to supervisorScope
                    }
                }
            }
        }

        // Example 12 using CoroutineScopes w/ CoroutineExceptionHandler + coroutineScope
        //   coroutineScope is used to cancel all the coroutines in the scope when any coroutines in the scope fail.
        if (false) {
            // Must be installed into the ROOT coroutine
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("CoroutineExceptionHandler Caught Exception: ${throwable.message}")
            }

            CoroutineScope(Dispatchers.Main + handler).launch {
                coroutineScope {
                    launch {
                        delay(500L)

                        // propagates up to top coroutine and is handled by the exception handler
                        throw Exception("Coroutine 1 failed - Example 12 ")
                    }
                    launch {
                        delay(600L)  // takes a little longer than "Coroutine 1"
                        println("Coroutine 2 finished - Example 12 ") // never completes now due to coroutineScope
                    }
                }
            }
        }

        // Example 13 show WRONG way to handle exceptions in coroutines
        // - handling ALL exceptions and *not* separating out CancellationException
        if (false) {
            lifecycleScope.launch {
                val job = launch {
                    try {
                        println("Coroutine 1 - starting simulated network call - Example 13 ")

                        delay(500L) // cancellationException is thrown *here*
                    } catch (e: Exception) {  // catching all exceptions *including* CancellationException
                        println("Coroutine 1 - Caught Exception: ${e.message}") // cancellationException is handled here and NOT propagated up to top coroutine
                    }

                    // ** UNEXPECTED BEHAVIOR ** - CancellationException is *NOT* propagated up to top coroutine!!!
                    // This prints because CancellationException is handled in above try/catch and *NOT* propagated up to top coroutine
                    println("Coroutine 1 finished - Example 13 ")
                }

                delay(300L)
                println("Coroutine 2 - Cancelling Coroutine 1 - Example 13 ")
                job.cancel()  // throws cancellationException and caught in the "delay()" above
            }

        }

        // Example 14 show correct way to handle exceptions in coroutines - capture & handle only specific exceptions
        // - only handling job-context specific exceptions, allows CancellationException to propagate up to top coroutine
        if (false) {
            lifecycleScope.launch {
                val job = launch {
                    try {
                        println("Coroutine 1 - starting simulated network call - Example 14 ")

                        // simulated network call
                        delay(500L) // cancellationException is thrown *here*
                    } catch (e: HttpRetryException) {  // catching ONLY SPECIFIC exceptions (ie: just for the network calls)
                        // NOTE: CancellationExceptions are propagated up to top coroutine.

                        println("Coroutine 1 - Caught Exception: ${e.message}")
                    }

                    // NEVER prints after CancellationException is propagated up to top coroutine and coroutine is cancelled
                    println("Coroutine 1 finished - Example 14 ")
                }

                delay(300L)
                println("Coroutine 2 - Cancelling Coroutine 1 - Example 14 ")
                job.cancel()  // throws cancellationException and caught in the "delay()" above (simulated network call)
            }
        }

        // Example 15 shows CORRECT way to handle exceptions in coroutines - manually re-throw CancellationExceptions
        if (false) {

            lifecycleScope.launch() {
                val job = launch {
                    try {
                        println("Coroutine 1 - starting simulated network call - Example 15 ")

                        // simulated network call
                        delay(500L) // cancellationException is thrown here
                    } catch (e: Exception) {  // catching *ALL* exceptions (and checking for CancellationException later in catch block)
                        println("Coroutine 1 - Caught Exception: ${e.message}")

                        if(e is CancellationException) {
                            println("Coroutine 1 - rethrowing CancellationException - Example 15 ")
                            throw e // re-throw cancellationException to propagate up to top coroutine
                        }
                    }

                    // NEVER prints - after cancellationException is propagated up to top coroutine and coroutine is cancelled
                    println("Coroutine 1 finished - Example 15 ")
                }

                delay(300L)
                println("Coroutine 2 - Cancelling Coroutine 1 - Example 15 ")
                job.cancel()  // throws cancellationException and caught in the "delay()" above (simulated network call)
            }
        }

        // Example 16 shows CORRECT way to handle exceptions in coroutines - using CoroutineExceptionHandler & custom Exception
        if (true) {

            // Does *NOT* catch CancellationException, but will catch all other exceptions
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("CoroutineExceptionHandler Caught Exception: ${throwable.message}")
            }

            lifecycleScope.launch(handler) {
                val job = launch {
                    try {
                        println("Coroutine 1 - starting simulated network call - Example 16 ")

                        // simulated network call
                        delay(500L) // cancellationException is thrown here
                    } catch (e: Exception) {  // catching *ALL* exceptions (and checking for CancellationException later in catch block)
                        println("Coroutine 1 - Caught Exception: ${e.message}")

                        if(e is CancellationException) {
                            println("Coroutine 1 - throwing custom exception - Example 16 ")
                            throw Exception("Custom Exception") // re-throw custom exception to propagate up to handler
                        }
                    }

                    // NEVER prints - after cancellationException is propagated up to top coroutine and coroutine is cancelled
                    println("Coroutine 1 finished - Example 16 ")
                }

                delay(300L)
                println("Coroutine 2 - Cancelling Coroutine 1 - Example 16 ")
                job.cancel()  // throws cancellationException and caught in the "delay()" above (simulated network call)
            }
        }


        setContent {
            CoroutineCancellationExceptionTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CoroutineCancellationExceptionTheme {
        Greeting("Android")
    }
}