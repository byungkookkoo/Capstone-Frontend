package com.example.capstone_frontend

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import io.socket.client.Socket
import kotlinx.android.synthetic.main.activity_intro.*
import kotlinx.android.synthetic.main.activity_snaptalk.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


class SnaptalkActivity : AppCompatActivity() {

    lateinit var mSocket: Socket // for socket
    private val gson = Gson()

    val REQUEST_IMAGE_CAPTURE = 1 // 카메라 사진 촬영 요청 코드
    lateinit var curPhotoPath: String // 사진 경로 값

    private val REQUEST_EXTERNAL_STORAGE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snaptalk)

        val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val readPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (readPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
        }

        setPermission() // 최초 권한 체크

        btn_camera.setOnClickListener {
            takeCapture() // 기본 카메라 앱 실행하여 사진 촬영
        }

        btn_chat.setOnClickListener {
            initUI()
        }
    }

    private fun initUI() {
        // 다크 모드 비활성화
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val username = intent.getStringExtra("username").toString()
        val roomNumber = intent.getStringExtra("roomNumber").toString()

        val intent = Intent(applicationContext, ChatActivity::class.java)
        intent.putExtra("username", username)
        intent.putExtra("roomNumber", roomNumber)
        startActivity(intent)
    }

    private fun takeCapture() { // 카메라 촬영
        // 기본 카메라 앱 실행
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e("Camera Error", ex.toString())
                    null
                }

                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile( // 보안 설정
                        this,
                        "com.example.capstone_frontend.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    private fun createImageFile(): File { // 이미지 파일 생성
        val timestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timestamp}_", ".jpg", storageDir)
            .apply { curPhotoPath = absolutePath }
    }

    // startActivityForResult를 통해 기본 카메라 앱으로부터 받아온 사진 결과 값
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val bitmap: Bitmap
            val img_uri: Uri = Uri.fromFile(File(curPhotoPath))
            val file = File(curPhotoPath)
            if (Build.VERSION.SDK_INT < 28) { // 안드로이드 9.0 (Pie) 버전보다 낮은 경우
                bitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(file))
                img_picture.setImageBitmap(bitmap)
            } else { // 안드로이드 9.0 (Pie) 버전보다 높은 경우
                val decode = ImageDecoder.createSource(
                    this.contentResolver,
                    Uri.fromFile(file)
                )
                bitmap = ImageDecoder.decodeBitmap(decode)
                img_picture.setImageBitmap(bitmap)
            }
            savePhoto(img_uri)
        }
    }
    @Throws(IOException::class)
    fun getBytes(image_uri: Uri?): ByteArray {
        val iStream: InputStream? = contentResolver.openInputStream(image_uri!!)
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024 // 버퍼 크기
        val buffer = ByteArray(bufferSize) // 버퍼 배열
        var len = 0

        if (iStream != null) { // InputStream에서 읽어올 게 없을 때까지 바이트 배열에 쓴다.
            while (iStream.read(buffer).also({ len = it }) != -1) byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }
    private fun savePhoto(image_uri: Uri) { // 갤러리에 저장
        val values = ContentValues()
        val fileName = "hyojason" + System.currentTimeMillis() + ".png"
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/*")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val contentResolver = contentResolver
        val item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        try {
            val pdf = contentResolver.openFileDescriptor(item!!, "w", null)
            if (pdf == null) {
                Log.d("Camera", "null")
            } else {
                val inputData: ByteArray = getBytes(image_uri)
                val fos = FileOutputStream(pdf.fileDescriptor)
                fos.write(inputData)
                fos.close()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(item!!, values, null, null)
                }
                galleryAddPic(fileName) // 갱신
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Log.d("camera", "FileNotFoundException  : " + e.getLocalizedMessage())
        } catch (e: Exception) {
            Log.d("camera", "FileOutputStream = : " + e.message)
        }
        Toast.makeText(this, "사진이 앨범에 저장되었습니다.", Toast.LENGTH_SHORT).show()
    }
    private fun galleryAddPic(Image_Path: String) {
        Log.d("camera", "갱신 : $Image_Path")

        val file = File(Image_Path)
        MediaScannerConnection.scanFile(
            getApplicationContext(), arrayOf(file.toString()),
            null, null
        )
    }

    /*
    TedPermission 설정
    */
    private fun setPermission() {
        val permission = object : PermissionListener {
            override fun onPermissionGranted() { // 설정해놓은 위험 권한들이 허용 되었을 경우 수행
                Toast.makeText(this@SnaptalkActivity, "권한이 허용 되었습니다.", Toast.LENGTH_SHORT).show()
            }

            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) { // 설정해놓은 위험 권한들 중 거부한 경우 수행
                Toast.makeText(this@SnaptalkActivity, "권한이 거부 되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        TedPermission.with(this)
            .setPermissionListener(permission)
            .setRationaleMessage("카메라 앱을 사용하시려면 권한을 허용해주세요.")
            .setDeniedMessage("권한을 거부하셨습니다. [앱 설정] -> [권한] 항목에서 허용해주세요.")
            .setPermissions(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA
            )
            .check()
    }
}