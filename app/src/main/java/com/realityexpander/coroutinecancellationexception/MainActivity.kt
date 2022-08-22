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
import kotlinx.coroutines.NonCancellable.join
import java.net.HttpRetryException

// Reference this video: https://www.youtube.com/watch?v=VWlwkqmTLHc

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Example 1
        // No try/catch blocks are in local launch scope, so the exception propagates
        //   to the top level and CRASHES.
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
                            // Exception MUST be caught & handled in local launch scope, or exception
                            //   will propagate to top level coroutine and crash.
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
        // Exception propagates up to top level & CRASHES.
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

        // Example 3.5 (2 inner coroutines, with try/catch in ALL scopes EXCEPT the local launch scope where exception occurs)
        // Exception propagates up to top level & CRASHES.
        // Even if the containing launch block has a try/catch, the exception STILL propagates
        //   up to the top level coroutine because it was not handled in the launch scope block that caused the exception.
        if (false) {
            lifecycleScope.launch {
                try {
                    launch {
                        try {
                            launch {

                                // Exception is NOT handled & propagates up to top coroutine
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

                    // Propagates up to top coroutine and not handled.
                    // Will *NOT* immediately crash because async coroutine is not yet complete.
                    throw Exception("Exception Example 5")

                    "Result"
                }
                println("string: ${string.await()}")

                string
            }

            // NOTICE: No `deferredString.await()` here.
            // The async coroutine never completes, so no crash occurs!
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
        // ** WRONG WAY TO HANDLE THIS - DON'T DO THIS **
        if (false) {
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

            // Complete the deferredString async here, no crash because exception is handled by the try/catch block.
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

        // Example 7.5 inner async coroutine, with outer async. Exception is thrown in inner async coroutine,
        // Second coroutine completes the Async & exception is handled by handler.
        // Uses CoroutineExceptionHandler to handle exception. No crash.
        // ** RIGHT WAY TO HANDLE THIS - DO THIS **
        if (false) {
            // Must install this in root coroutine scope (top-level async or launch)
            val handler = CoroutineExceptionHandler { _, e ->
                println("CoroutineExceptionHandler Caught Exception: ${e.message}")
            }

            val deferredString = lifecycleScope.async {
                val string = async {
                    println("About to get string...")
                    delay(500L)

                    // propagated up to top coroutine and not handled,
                    // *will* crash here when async coroutine deferredString is .await()'ed.
                    throw Exception("Exception Example 7.5")

                    "Result"
                }

                println("About to print string...")
                println("string: ${string.await()}") // Completes the `string` async coroutine & throws the exception.

                string
            }

            // Complete the deferredString async here, no crash because exception is handled by the handler.
            // ** RIGHT WAY TO HANDLE THIS - DO THIS **
            // USE CoroutineExceptionHandler to handle exception.
            lifecycleScope.launch(handler) {
                println("About to print deferredString...")
                println("deferredString: ${deferredString.await()}")  // Completes the `deferredString` async block & handler catches exception and handles it.
            }
        }

        // Example 7.6 simpler example - inner async coroutine, with outer async. Exception is thrown in inner async coroutine,
        // `string.await()` completes the async & exception is handled by handler.
        // Uses CoroutineExceptionHandler to handle exception. No crash.
        // ** RIGHT WAY TO HANDLE THIS - DO THIS **
        if (false) {
            // Must install this in root coroutine scope
            // (top-level ie: <scope>.launch(handler).  Using <scope>.async(handler) doesn't work)
            val handler = CoroutineExceptionHandler { _, e ->
                println("CoroutineExceptionHandler Caught Exception: ${e.message}")
            }

            lifecycleScope.launch(handler) {
                val string = lifecycleScope.async {
                    println("About to get string...")
                    delay(500L)

                    // propagated up to top coroutine and not handled,
                    // *will* crash here when async coroutine deferredString is .await()'ed.
                    throw Exception("Exception Example 7.6")

                    "Result"
                }

                println("About to print string...")
                println("string: ${string.await()}") // Completes the `deferred string async` coroutine & throws the exception.
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

                        if (e is CancellationException) {
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

                        if (e is CancellationException) {
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

        // Example 17 shows use of coroutineScope to re-throw exceptions of its children
        //   & supervisorScope to handle exceptions of its children.
        // `coroutineScope` catches & re-throws the failing exception of any child and we are
        //    catching them here in the catch block INSTEAD of propagating them up to top coroutine,
        //    and this also cancels all other child coroutines.
        // `supervisorScope` catches the failing exceptions of all children, and propagates them
        //    to the `CoroutineExceptionHandler`, and does NOT cancel the other children.
        // - NOTE: `coroutineScope` is NOT a coroutine itself.
        // - NOTE: Once a child of coroutineScope fails, ALL CHILD COROUTINES ARE CANCELLED.
        // https://medium.com/mindful-engineering/exception-handling-in-kotlin-coroutines-fd08e622360e
        if(true) {
            // Must be used with supervisorScope AND must be installed into the ROOT coroutine.
            val handler = CoroutineExceptionHandler { _, throwable ->
                println("CoroutineExceptionHandler Caught Exception: ${throwable.message}")
            }

            lifecycleScope.launch {            // Note: No need to use `handler` for coroutineScope, because the try/catch will handle the exceptions.
//            lifecycleScope.launch(handler) { // Note: *MUST* use `handler` for supervisorScope, or have exceptions handled locally with try/catch in the child coroutine, or they will be propagated up to the top coroutine.
                try {
                                            // WITHOUT using special enclosing scopes, any exception thrown by a child coroutine is CHANGED to a CancellationException! (UNEXPECTED BEHAVIOR)
                                            //   - Note: The child's original exception is stored in the `.cause` property of the CancellationException.
                    coroutineScope {        // WHEN using `coroutineScope`, the exception from the child coroutine is not changed and caught as normal.
//                    supervisorScope {     // WHEN using `supervisorScope`, any exception of any child does not cancel the entire scope. (IE: other children are not cancelled.) (MUST USE `handler`)

                        launch {
                            delay(200)
                            println("Coroutine 1 - starting simulated network call - Example 17...")
                            throw HttpRetryException("Coroutine 1 - Simulated Error - from child", 404)
                        }

                        launch {
                            println("Coroutine 2 - starting simulated processing work - Example 17...")
                            delay(200)
                            println("Coroutine 2 - finished simulated processing work - Example 17...")
                        }

                        delay(500L) // comment out to see catch block for this coroutine
                        // When using supervisorScope, since this is not a child coroutine, it is caught in the try/catch block below (not the handler!)
                        val deferredResult1 = async(Dispatchers.IO) {
                            println("Coroutine 3 - retrieving deferredResult1...")
                            throw IllegalStateException("Coroutine 3 - Simulated Error - from sibling coroutine")

                            "result from async"
                        }
                        println("About to print deferredResult1...")
                        println(deferredResult1.await())
                    }
                } catch (e: CancellationException) {
                    println("Catch handled CancellationException: $e") // If block is NOT enclosed with `coroutineScope`, all exceptions are treated as CancellationExceptions.
                    println("  e.cause: ${e.cause}")
                    println("  e.message: ${e.message}")
                } catch (e: Exception) {
                    // If block is enclosed by `coroutineScope`, the original exception from the child coroutine is caught here.
                    // If NOT enclosed by `coroutineScope` the exception is a CHANGED to a generic CancellationException,
                    //   and the child's original exception is stored in the CancellationException's `.cause` property.
                    println("Catch handled GENERAL Exception: $e")
                    println("  e.cause: ${e.cause}")     // When NOT enclosed with `coroutineScope`, this contains the Exception from the child coroutine.
                    println("  e.message: ${e.message}")
                }
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

//////////////////////////////////////////////////////////////////////////////////////
// Show delegation using by
//////////////////////////////////////////////////////////////////////////////////////


interface IHardwareMonitor {

    fun observeBatteryChanges()

    fun observeNetworkChanges()

}
class HardwareMonitorImpl : IHardwareMonitor {

    override fun observeBatteryChanges() {
        println("observeBatteryChanges()")
    }

    override fun observeNetworkChanges() {
        print("observeNetworkChanges()")
    }

}
// For user action tracking:

interface ITrackingService {

    fun trackUserAction(action: String)

}
class UserTrackingImpl : ITrackingService {

    override fun trackUserAction(action: String) {
        println("trackUserAction($action)")
    }

}

// Now if our Activity uses both hardware monitor and tracking service we can implement the
// IHardwareMonitor and ITrackingService class:

class MainActivity2 : ComponentActivity(),
    IHardwareMonitor by HardwareMonitorImpl(), // delegation
    ITrackingService by UserTrackingImpl() // delegation
{

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeBatteryChanges()
        observeNetworkChanges()
    }
    override fun onPause() {
        super.onPause()
        trackUserAction("Activity paused")
    }
}

// Or if our Activity uses only IHardwareMonitor class, we can just plug it into it:

class LoginActivity : ComponentActivity(),
    IHardwareMonitor by HardwareMonitorImpl() { // delegation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeBatteryChanges()
        observeNetworkChanges()
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