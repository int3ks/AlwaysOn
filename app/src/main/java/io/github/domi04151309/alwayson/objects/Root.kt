package io.github.domi04151309.alwayson.objects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.*

object Root {

    fun request(): Boolean {
        val p: Process
        return try {
            p = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(p.outputStream)
            os.writeBytes("echo access granted\n")
            os.writeBytes("exit\n")
            os.flush()
            true
        } catch (e: Exception) {
            Log.e(Global.LOG_TAG, e.toString())
            false
        }
    }


    fun WriteSupportBatch(con: Context) {
        val SupportBatch = "adb shell pm grant "+ con.packageName + " android.permission.WRITE_SECURE_SETTINGS\n\rTimeout 5"
        try {
            val newFolder = con.getExternalFilesDir(null)
            if (!newFolder!!.exists()) {
                newFolder.mkdir()
            }

            var file = File(newFolder, "GrantPermissions.bat")
            if (file.exists()) {
                return
            }
            file.createNewFile()
            val fos: FileOutputStream
            val data = SupportBatch.toByteArray()
            fos = FileOutputStream(file)
            fos.write(data)
            fos.flush()
            fos.close()
            con.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
        } catch (ignore: java.lang.Exception) {
            Log.e(con.packageName,"writesupportbatch", ignore)
        }
    }


    fun shellNR(command: String){

        var su = Runtime.getRuntime().exec("sh");
        var outputStream =  DataOutputStream(su.getOutputStream());
        outputStream.writeBytes(command)
        outputStream.flush()
        outputStream.close();
        su.waitFor();
       // var bufferedReader =  BufferedReader( InputStreamReader(su.getErrorStream()));
        var bufferedReader =  BufferedReader( InputStreamReader(su.inputStream));

        var line = "";
        line = bufferedReader.readLine()
        while (line != null) {
            Log.e("Always",line);
            line = bufferedReader.readLine()
        }

        bufferedReader.close();


    }

    fun shell(command: String) {
        try {
            val p = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", command))
            p.waitFor()
        } catch (e: Exception) {
            Log.e("Superuser", e.toString())
        }
    }
}
