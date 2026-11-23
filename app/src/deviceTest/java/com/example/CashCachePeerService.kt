package com.example

import android.app.Service
import android.content.Intent
import android.os.*
import com.example.data.entity.PaymentEntity
import com.example.data.firebase.*
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Test-variant-only peer. Never packaged in the product; no exported or production endpoint. */
class CashCachePeerService : Service() {
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var auth: FirebaseAuth?=null
    private var db: FirebaseFirestore?=null
    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper()) { message ->
        val command=message.what; val args=Bundle(message.data);val reply=message.replyTo
        jobs.launch {
            val result=runCatching { withTimeout(60000) { handle(command,args) } }
            val response=result.getOrElse { Bundle().apply { putString("error",it.stackTraceToString()) } }
            reply.send(Message.obtain().apply { data=response })
        }
        true
    }) }
    override fun onBind(intent: Intent) = messenger.binder
    override fun onDestroy() { jobs.cancel();super.onDestroy() }
    private suspend fun handle(command: Int, args: Bundle): Bundle {
        check(BuildConfig.DEVICE_TEST && packageName.endsWith(".devicetest"))
        val default=FirebaseApp.initializeApp(this) ?: FirebaseApp.getInstance()
        check(default.options.projectId=="demo-aidhunt-v16")
        if(command==1) {
            val name=requireNotNull(args.getString("app"));require(name.startsWith("cash-partner-"))
            val app=FirebaseApp.initializeApp(this,default.options,name)
            auth=FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1",19099) }
            db=FirebaseFirestore.getInstance(app).apply { useEmulator("127.0.0.1",18180) }
            if(args.getBoolean("create")) auth!!.createUserWithEmailAndPassword(requireNotNull(args.getString("email")),"SyntheticOnly123!").await()
            return Bundle().apply { putString("uid",auth!!.currentUser?.uid);putInt("pid",Process.myPid()) }
        }
        val actor=requireNotNull(auth).currentUser?.uid.orEmpty()
        val store=requireNotNull(db)
        val ws=args.getString("ws").orEmpty();require(ws.startsWith("ws_cash_native_"))
        val repo=CashBalanceAdjustmentRepository(store,auth!!)
        return when(command) {
            2 -> {
                repo.save(ws,actor,requireNotNull(args.getString("operation")),args.getLong("revision"),
                    args.getLong("target"),args.getLong("base"),requireNotNull(args.getString("reason")))
                Bundle().apply { putLong("total",repo.readTotal(ws,actor)) }
            }
            3 -> {
                val state=repo.observe(ws,actor).first { it.confirmed && it.revision==args.getLong("revision") }
                val receipts=store.collection("workspaces").document(ws).collection("collections").get().await().documents.map {
                    PaymentEntity(id=it.id.toLong(),workspaceId=ws,amount=it.getDouble("amount")!!,
                        status=it.getString("status")!!,paymentMethod=it.getString("paymentMethod")!!)
                }
                val cash=FinancialCalculationEngine.cashAvailable(receipts,emptyList(),emptyList(),FinancialScope(workspaceId=ws))+
                    FinancialCalculationEngine.cashAdjustmentDelta(state.edits,FinancialScope(workspaceId=ws))
                Bundle().apply { putLong("total",state.totalAdjustmentPaise);putInt("count",state.edits.size)
                    putString("firstActor",state.edits.minBy { it.revision }.actorUid);putLong("cash",(cash*100).toLong()) }
            }
            4 -> {
                store.disableNetwork().await()
                val total=repo.readTotal(ws,actor)
                val cached=repo.observe(ws,actor).first { it.loaded }
                Bundle().apply { putLong("total",total);putInt("count",cached.edits.size);putBoolean("confirmed",cached.confirmed) }
            }
            5 -> {
                store.terminate().await()
                Bundle().apply { putInt("pid",Process.myPid()) }
            }
            6 -> {
                auth!!.signOut()
                val denied=runCatching { repo.readTotal(ws,actor) }.isFailure
                Bundle().apply { putBoolean("denied",denied) }
            }
            else -> error("Unknown synthetic peer command")
        }
    }
}
