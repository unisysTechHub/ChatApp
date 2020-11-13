package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.amazonaws.amplify.generated.graphql.CreateMessageMutation
import com.amazonaws.amplify.generated.graphql.ListMessagesQuery
import com.amazonaws.amplify.generated.graphql.OnCreateMessageSubscription
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.amazonaws.mobileconnectors.appsync.sigv4.CognitoUserPoolsAuthProvider
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.core.Amplify
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import type.CreateMessageInput
import javax.annotation.Nonnull
import kotlin.reflect.KClass


class MainActivity : AppCompatActivity() {
    private var mAWSAppSyncClient: AWSAppSyncClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

       val chatViewModel = ViewModelProvider(this,AppViewModelProviderFactory()).get(ChatViewModel::class.java)
   // Amplify.Auth.signOut({ Log.d("@AWS", "signOut success") }) { error: AuthException -> Log.d("@AWS", error.message!!) }
        val button = findViewById<Button>(R.id.sign_in)
        button.setOnClickListener { view ->
            Amplify.Auth.signInWithWebUI(this,
                    { result: AuthSignInResult ->
                        Log.i("AuthQuickStart", result.toString())
                        var authProvider = object  : CognitoUserPoolsAuthProvider
                        {
                            override fun getLatestAuthToken(): String {

                             return  result.nextStep.additionalInfo?.get("token") ?: "0"
                            }
                        }

                        mAWSAppSyncClient = AWSAppSyncClient.builder()
                        .context(getApplicationContext())
                                .cognitoUserPoolsAuthProvider(authProvider)
                        .awsConfiguration( AWSConfiguration(getApplicationContext()))
                        // If you are using complex objects (S3) then uncomment
                        //.s3ObjectManager(new S3ObjectManagerImplementation(new AmazonS3Client(AWSMobileClient.getInstance())))
                        .build();
                query()
                        subscribe()
                         Log.d("@AWS", Amplify.Auth.currentUser.username)

                    },
                    { error: AuthException -> Log.e("AuthQuickStart", error.toString()) })
//            Amplify.Auth.signIn("chatuser","password",{ result: AuthSignInResult ->
//                Log.i("AuthQuickStart", result.toString())
////                Log.d("@AWS", Amplify.Auth.currentUser.username)
//                mAWSAppSyncClient = AWSAppSyncClient.builder()
//                        .context(getApplicationContext())
//                        .awsConfiguration( AWSConfiguration(getApplicationContext()))
//                        // If you are using complex objects (S3) then uncomment
//                        //.s3ObjectManager(new S3ObjectManagerImplementation(new AmazonS3Client(AWSMobileClient.getInstance())))
//                        .build();
//                query()
//                mutation()
//                subscribe()
//            },
//                    { error: AuthException -> Log.e("AuthQuickStart", error.toString()) })
//
        }



    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.actionmenu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sign_out -> {
                Amplify.Auth.signOut({ Log.d("@AWS", "signOut success") }) { error: AuthException -> Log.d("@AWS", error.message!!) }

            }

        }
        return super.onOptionsItemSelected(item)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AWSCognitoAuthPlugin.WEB_UI_SIGN_IN_ACTIVITY_CODE) {
            println("@AWS signed in ")
            Amplify.Auth.handleWebUISignInResponse(data)
        }

    }

    fun mutation() {
        val createMessageInput: CreateMessageInput = CreateMessageInput.builder()
                .name("Ramesh")
                .message("message from android app put item resolver test")
                .build()
        mAWSAppSyncClient!!.mutate(CreateMessageMutation.builder().input(createMessageInput).build()).enqueue(mutationCallback)

    }
    private val mutationCallback: GraphQLCall.Callback<CreateMessageMutation.Data?> =
        object : GraphQLCall.Callback<CreateMessageMutation.Data?>() {
            override fun onFailure(e: ApolloException) {
                Log.e("@Error", e.toString());
            }

            override fun onResponse(response: Response<CreateMessageMutation.Data?>) {
                Log.i("@Results", "Added Message" + response.data().toString());
            }

        }
    fun query() {
        mAWSAppSyncClient!!.query(ListMessagesQuery.builder().build())
            .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
            .enqueue(messagesListCallback)
    }

    private val messagesListCallback: GraphQLCall.Callback<ListMessagesQuery.Data?> =
        object : GraphQLCall.Callback<ListMessagesQuery.Data?>() {


            override fun onFailure(@Nonnull e: ApolloException) {
                Log.e("@ERROR", e.toString())
            }

            override fun onResponse(response: com.apollographql.apollo.api.Response<ListMessagesQuery.Data?>) {
                Log.i("@Results", response.data()?.listMessages()?.items().toString());
            }
        }

    private var subscriptionWatcher: AppSyncSubscriptionCall<OnCreateMessageSubscription.Data>? = null

    private fun subscribe() {
        val subscription: OnCreateMessageSubscription = OnCreateMessageSubscription.builder().build()
        subscriptionWatcher = mAWSAppSyncClient!!.subscribe(subscription);

        subscriptionWatcher?.execute(subCallback)
    }
    private val subCallback: AppSyncSubscriptionCall.Callback<OnCreateMessageSubscription.Data> =
        object : AppSyncSubscriptionCall.Callback<OnCreateMessageSubscription.Data> {
            override fun onResponse(@Nonnull response: Response<OnCreateMessageSubscription.Data>) {
                Log.i("@Subscription", response.data().toString())
            }

            override fun onFailure(@Nonnull e: ApolloException) {
                Log.e("@Subscription", e.toString())
            }

            override fun onCompleted() {
                Log.i("@Subscription", "Subscription completed")
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        Amplify.Auth.signOut({ Log.d("@AWS", "signOut success") }) { error: AuthException -> Log.d("@AWS", error.message!!) }

    }
}