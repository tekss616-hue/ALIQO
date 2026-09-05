package com.aliqo.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max

@Composable
fun rememberCleanDarkEdgeBitmap(@DrawableRes resId:Int):ImageBitmap{
    val context=LocalContext.current
    return remember(resId){
        val original=BitmapFactory.decodeResource(context.resources,resId)
        val maxSide=512
        val scale=max(original.width,original.height).toFloat()/maxSide.toFloat()
        val bitmap=if(scale>1f){
            Bitmap.createScaledBitmap(original,(original.width/scale).toInt().coerceAtLeast(1),(original.height/scale).toInt().coerceAtLeast(1),true)
        }else original.copy(Bitmap.Config.ARGB_8888,true)
        if(bitmap.config!=Bitmap.Config.ARGB_8888){
            val copy=bitmap.copy(Bitmap.Config.ARGB_8888,true)
            if(bitmap!==original)bitmap.recycle()
            cleanDarkConnectedEdges(copy).asImageBitmap()
        }else{
            cleanDarkConnectedEdges(bitmap).asImageBitmap()
        }
    }
}

private fun cleanDarkConnectedEdges(bitmap:Bitmap):Bitmap{
    val w=bitmap.width
    val h=bitmap.height
    val px=IntArray(w*h)
    bitmap.getPixels(px,0,w,0,0,w,h)
    val visited=BooleanArray(px.size)
    val queue=IntArray(px.size)
    var head=0
    var tail=0
    fun brightness(c:Int):Int{
        val r=(c shr 16) and 255
        val g=(c shr 8) and 255
        val b=c and 255
        return max(r,max(g,b))
    }
    fun enqueue(i:Int){
        if(i<0||i>=px.size||visited[i])return
        if(brightness(px[i])>=72)return
        visited[i]=true
        queue[tail++]=i
    }
    for(x in 0 until w){enqueue(x);enqueue((h-1)*w+x)}
    for(y in 0 until h){enqueue(y*w);enqueue(y*w+w-1)}
    while(head<tail){
        val i=queue[head++]
        val x=i%w
        val y=i/w
        if(x>0)enqueue(i-1)
        if(x<w-1)enqueue(i+1)
        if(y>0)enqueue(i-w)
        if(y<h-1)enqueue(i+w)
    }
    for(i in px.indices){
        if(!visited[i])continue
        val v=brightness(px[i])
        val a=(((v-24).coerceIn(0,48))*255/48).coerceIn(0,255)
        px[i]=(px[i] and 0x00FFFFFF) or (a shl 24)
    }
    bitmap.setPixels(px,0,w,0,0,w,h)
    return bitmap
}
