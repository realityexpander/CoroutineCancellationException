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

// Reference this video: https://www.youtube.com/watch?v=VWlwkqmTLHc

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Example 1
        // No try/catch blocks are in local launch scope, so the exception propagates to the top level and crashes.
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        throw Exception("Exception Example 1") // Propagates to top level & crashes here (no try/catch in local launch scope)
                    }
                } catch (e: Exception) {
                    println("Caught Exception: ${e.message}")
                }
            }
        }

        // Example 2 (1 inner coroutine)
        // Exception is caught locally and not propagated, using a try/catch in local launch
        //   scope where exception occurs.
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        try {
                            throw Exception("Exception Example 2")
                        } catch (e: Exception) {
                            // MUST be caught & handled in local launch scope, or exception
                            //   will propagate to top level coroutine and crash
                            println("Caught inner Exception: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    println("Caught outer Exception: ${e.message}")
                }
            }
        }

        // Example 3 (2 inner coroutines)
        // Exception propagates up to top level & crashes.
        //   - no local try/catch blocks where exception occurred.
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        launch {

                            // Exception is *NOT* handled so propagates up
                            // & not handled & propagates up to top coroutine
                            // & CRASHES here
                            throw Exception("Exception Example 3")
                        }
                    }
                } catch (e: Exception) {
                    println("Caught outer Exception: ${e.message}")
                }
            }
        }

        // Example 3.5 (2 inner coroutines, with try/catch in all scopes EXCEPT local launch scope where exception occurs)
        // Exception propagates up to top level & crashes.
        // Even if the containing launch block has a try/catch, the exception STILL propagates
        //   up to the top level coroutine because it was not handled in the launch scope block that caused the exception.
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        try {
                            launch {

                                // Exception is not handled & propagates up
                                // & not handled & propagates up to top coroutine
                                // & CRASHES here
                                throw Exception("Exception Example 3.5")
                            }
                        } catch (e: Exception) { // **NOT** caught here
                            println("Caught inner Exception: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {  // **NOT** caught here EITHER!
                    println("Caught outer Exception: ${e.message}")
                }
            }
        }


        // Example 4 inner async coroutine, outer launch.
        // Crashes because the inner async coroutine throws unhandled exception and
        //   propagates up to the top and crashes.
        if (false) {
            lifecycleScope.launch {
                val string = async {
                    delay(500L)

                    // propagates up to top coroutine and not handled, CRASHES here.
                    throw Exception("Exception Example 4")

                    "Result"
                }
                println("string: ${string.await()}") // completes here and crashes above.
            }
        }

        // Example 5 - inner async coroutine, outer async, outer async never completes.
        // No crash.
        // Inner async never completes because `deferredString.await()` is never called.
        if (false) {
            val deferredString = lifecycleScope.async {
                val string = async {
                    delay(500L)

                    // Propagates up to top coroutine and not handled,
                    // Will *NOT* immediately crash because async coroutine is not yet complete.
                    throw Exception("Exception Example 5")

                    "Result"
                }
                println("string: ${string.await()}")

                string
            }

            // NOTICE: No `deferredString.await()` here. The async coroutine never completes, so no crash occurs.
        }

        // Example 6 inner async coroutine, second coroutine completes the async with an await.
        // Exception is thrown in inner async coroutine.
        // Second coroutine completes the Async & exception is NOT handled causing CRASH.
        if (false) {
            val deferredString = lifecycleScope.async {
                val string = async {
                    delay(500L)

                    // propagated up to top coroutine and not handled,
                    // *will* CRASH here when async coroutine deferredString is .await()'ed.
                    throw Exception("Exception Example 6")

                    "Result"
                }
                println("string: ${string.await()}")

                string
            }

            // Get the deferredString here, which completes the deferredString async coroutine.
            // This causes a CRASH due to unhandled exception.
            lifecycleScope.launch {
                println("deferredString: ${deferredString.await()}")  // causes crash
            }
        }

        // Example 7 inner async coroutine, with outer async. Exception is thrown in inner async coroutine,
        // Second coroutine completes the Async & exception is handled. No crash.
        if (true) {
            val deferredString = lifecycleScope.async {
                val string = async {
                    println("About to get string...")
                    delay(500L)

                    // propagated up to top coroutine and not handled,
                    // *will* crash here when async coroutine deferredString is .await()'ed.
                    throw Exception("Exception Example 7")

                    "Result"
                }

                println("About to print string...")
                println("string: ${string.await()}") // Completes the `string` async coroutine & throws the exception.

                string
            }

            // get the deferred string here, no crash because exception is handled
            // ** WRONG WAY TO HANDLE THIS - DON'T DO THIS **
            lifecycleScope.launch {
                try {
                    println("About to print deferredString...")
                    println("deferredString: ${deferredString.await()}")  // Completes the `deferredString` async block & catches exception and handles it.
                } catch (e: Exception) {
                    println("Second coroutine Caught Exception: ${e.message}")
                }
            }
        }

        // Example 8 using CoroutineExceptionHandler.
        // Exception is handled by CoroutineExceptionHandler.
        if (false) {
            // Must be installed into the ROOT coroutine
            // NOTE: Does not handle CancellationException
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("Caught Exception: ${throwable.message}")
            }

            lifecycleScope.launch(handler) {

                // Exceptions propagated up to top coroutine and handled by the CoroutineExceptionHandler (but *NOT* CancellationException)
                throw Exception("Exception Example 8")
            }
        }

        // Example 9 using CoroutineScopes w/o CoroutineExceptionHandler.
        // Exceptions are not handled and causes crash.
        if (false) {
            CoroutineScope(Dispatchers.Main).launch {
                launch {
                    delay(500L)
                    throw Exception("Coroutine 1 failed - Example 9 ")  // propagates up & crashes here
                }
                launch {
                    delay(600L)  // takes a little longer than "Coroutine 1"
                    println("Coroutine 2 finished - Example 9 ") // never prints, never completes
                }
            }
        }

        // Example 10 using CoroutineScopes w/ CoroutineExceptionHandler.
        if (false) {
            // Must be installed into the ROOT coroutine
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("CoroutineExceptionHandler Caught Exception: ${throwable.message}")
            }

            // Because there is no SuperVisor scope, if one of the coroutines fails, THE WHOLE SCOPE FAILS.
            CoroutineScope(Dispatchers.Main + handler).launch {
                launch {
                    delay(500L)

                    // propagates up to top coroutine and is handled by the exception handler
                    throw Exception("Coroutine 1 failed - Example 10 ")
                }
                launch {
                    delay(600L)  // takes a little longer than "Coroutine 1"
                    println("Coroutine 2 finished - Example 10 ") // never prints, never completes
                }
            }
        }


        // Example 11 using CoroutineScopes w/ CoroutineExceptionHandler + supervisorScope.
        //   supervisorScope allows all the coroutines in the scope to run EVEN IF ONE/MANY OF THE COROUTINES FAILS.
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

        // Example 12 using CoroutineScopes w/ CoroutineExceptionHandler + coroutineScope.
        //   coroutineScope will cancel ALL the coroutines in the scope when ANY COROUTINE IN THE SCOPE FAILS.
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

        // Example 13 shows WRONG way to handle exceptions in coroutines.
        // - handling ALL exceptions and *not* separating out CancellationException.
        if (false) {
            lifecycleScope.launch {
                val job = launch {
                    try {
                        println("Coroutine 1 - starting simulated network call - Example 13 ")

                        delay(500L) // cancellationException is thrown *here*
                    } catch (e: Exception) {  // catching all exceptions *including* CancellationException, this causes UNEXPECTED behavior.
                        println("Coroutine 1 - Caught Exception: ${e.message}") // CancellationException is handled here and *NOT* propagated up to top coroutine
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

        // Example 14 shows CORRECT way to handle exceptions in coroutines.
        // - OPTION 1: Capture & handle only specific exceptions.
        // - only handling job-context specific exceptions, allows CancellationException to propagate up to top coroutine.
        if (false) {
            lifecycleScope.launch {
                val job = launch {
                    try {
                        println("Coroutine 1 - starting simulated network call - Example 14 ")

                        // simulated network call
                        delay(500L) // cancellationException is thrown *here*
                    } catch (e: HttpRetryException) {  // catching ONLY SPECIFIC exceptions (ie: just for the network calls)
                        // NOTE: CancellationException is propagated up to top coroutine.

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

        // Example 15 shows CORRECT way to handle exceptions in coroutines.
        // - OPTION 2: Catch all exceptions & manually re-throw CancellationException.
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

        // Example 16 shows CORRECT way to handle exceptions in coroutines.
        // - OPTION 3: Using CoroutineExceptionHandler & custom Exception to handle CancellationException.
        if (false) {

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