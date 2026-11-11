package com.example.autofish

import android.app.Activity
import android.content.*
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import java.io.File
import kotlin.math.*

class CaptureToolsActivity : Activity() {
 private lateinit var preview: RegionView
 private lateinit var status: TextView
 private lateinit var record: Button
 private var region = "cast"
 private val prefs by lazy { getSharedPreferences("fish", MODE_PRIVATE) }
 private val latest by lazy { File(filesDir, "autofish_capture/latest.png") }
 private val receiver = object: BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { load() } }
 override fun onCreate(saved: Bundle?) {
  super.onCreate(saved)
  val root=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)); setBackgroundColor(Color.rgb(10,15,29)) }
  root.addView(text("Инструменты распознавания",20f,Color.WHITE))
  status=text("Сделай снимок Roblox. Служба AutoFish должна быть включена.",13f,Color.LTGRAY); root.addView(status)
  root.addView(btn("Сделать снимок экрана") { send(FishAccessibilityService.ACTION_CAPTURE); status.text="Запрашиваю снимок…" })
  record=btn("Начать запись кадров") {
   val active=prefs.getBoolean("recordingUi",false)
   send(if(active) FishAccessibilityService.ACTION_RECORD_STOP else FishAccessibilityService.ACTION_RECORD_START)
   prefs.edit().putBoolean("recordingUi",!active).apply()
   record.text=if(active) "Начать запись кадров" else "Остановить запись"
   status.text=if(active) "Запись остановлена." else "Запись идёт. Вернись в Roblox; сохраняются последние 120 кадров."
  }; root.addView(record)
  val row=LinearLayout(this).apply { orientation=0 }
  listOf("Заброс" to "cast","Шкала" to "gauge","Сектор" to "target","Маркер" to "marker").forEach { pair ->
   row.addView(btn(pair.first) { region=pair.second; preview.region=region; status.text="Выбрана область: "+pair.first+". Обведи её пальцем." },LinearLayout.LayoutParams(0,dp(44),1f))
  }; root.addView(row)
  root.addView(text("Выбери тип области и обведи её на снимке пальцем.",12f,Color.LTGRAY))
  preview=RegionView(this) { rect ->
   val b=preview.bitmap
   if(b!=null) {
    prefs.edit().putFloat("roi_"+region+"_left",rect.left/b.width).putFloat("roi_"+region+"_top",rect.top/b.height).putFloat("roi_"+region+"_right",rect.right/b.width).putFloat("roi_"+region+"_bottom",rect.bottom/b.height).apply()
    status.text="Область "+region+" сохранена."
   }
  }
  root.addView(preview,LinearLayout.LayoutParams(-1,0,1f).apply { setMargins(0,dp(6),0,dp(6)) })
  root.addView(btn("Обновить снимок") { load() })
  root.addView(btn("Готово") { finish() })
  setContentView(root)
  val f=IntentFilter(FishAccessibilityService.ACTION_CAPTURED)
  if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED) else { @Suppress("DEPRECATION") registerReceiver(receiver,f) }
  load()
 }
 override fun onDestroy(){ try{unregisterReceiver(receiver)}catch(_:Exception){}; super.onDestroy() }
 private fun send(a:String){ sendBroadcast(Intent(a).setPackage(packageName)) }
 private fun load(){
  if(latest.exists()){ val b=BitmapFactory.decodeFile(latest.absolutePath); if(b!=null){preview.setBitmap(b); val n=latest.parentFile?.listFiles()?.count{it.name.startsWith("frame_")}?:0; status.text="Снимок получен. Кадров записи: "+n; return} }
  val e=prefs.getInt("lastScreenshotError",-1); status.text=if(e>=0) "Ошибка снимка. Код: "+e else "Снимка пока нет. Нажми кнопку выше."
 }
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun text(s:String,z:Float,c:Int)=TextView(this).apply{text=s;textSize=z;setTextColor(c);setPadding(0,dp(6),0,dp(6))}
 private fun btn(s:String,a:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{a()}}
 private class RegionView(c:Context,val done:(RectF)->Unit):View(c){
  var region="cast"; var bitmap:Bitmap?=null; private val dest=RectF(); private val box=RectF(); private val p=Paint(3); private var sx=0f; private var sy=0f
  fun setBitmap(b:Bitmap){bitmap=b;invalidate()}
  override fun onDraw(canvas:Canvas){canvas.drawColor(Color.rgb(24,31,48));val b=bitmap?:return;val k=min(width.toFloat()/b.width,height.toFloat()/b.height);val w=b.width*k;val h=b.height*k;dest.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2);canvas.drawBitmap(b,null,dest,p);if(!box.isEmpty){p.color=0x5533ccff;p.style=Paint.Style.FILL;canvas.drawRect(box,p);p.color=Color.CYAN;p.style=Paint.Style.STROKE;p.strokeWidth=3f;canvas.drawRect(box,p);p.style=Paint.Style.FILL}}
  override fun onTouchEvent(e:MotionEvent):Boolean{when(e.action){0->{sx=e.x;sy=e.y;box.set(sx,sy,sx,sy);return true};1->{box.set(min(sx,e.x),min(sy,e.y),max(sx,e.x),max(sy,e.y));invalidate();return true};2->{val b=bitmap?:return true;val l=max(dest.left,min(sx,e.x));val t=max(dest.top,min(sy,e.y));val r=min(dest.right,max(sx,e.x));val d=min(dest.bottom,max(sy,e.y));box.set(l,t,r,d);if(r-l>5&&d-t>5){val kx=b.width/dest.width();val ky=b.height/dest.height();done(RectF((l-dest.left)*kx,(t-dest.top)*ky,(r-dest.left)*kx,(d-dest.top)*ky))};invalidate();return true}};return true}
 }
}