package com.example.onnx

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.onnx.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ai.onnxruntime.*
import android.annotation.SuppressLint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.collections.HashMap
import java.io.File // ⭐️ File 임포트 추가
import java.io.FileOutputStream // ⭐️ FileOutputStream 임포트 추가
import java.nio.LongBuffer

/**
 * src/main/assets에 bert.onnx와 vocab.txt를 넣어줘야함
 * assets 폴더가 없으면 만들기
 * Empty views activity 템플릿
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // KoBERT 토크나이저 관련 (간단화된 WordPiece 구현)
    private lateinit var vocab: Map<String, Int>
    private val maxSequenceLength = 128 // Python 변환 시 사용한 길이와 동일하게
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val unkToken = "[UNK]"
    private val padToken = "[PAD]"
    private val modelName = "bert.onnx"

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MainScope().launch {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.inferButton.isEnabled = false
            try {
                // ⭐️ IO 스레드에서 파일 복사 및 모델 초기화
                withContext(Dispatchers.IO) {
                    initTokenizer(applicationContext)
                    initOnnxRuntime(applicationContext) // ⭐️ 수정된 함수 호출
                }
                Toast.makeText(this@MainActivity, "모델 로드 및 토크나이저 초기화 완료", Toast.LENGTH_SHORT).show()
                binding.inferButton.isEnabled = true
            } catch (e: Exception) {
                Log.e("MainActivity", "초기화 실패: ${e.message}", e)
                Toast.makeText(this@MainActivity, "초기화 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }

        binding.inferButton.setOnClickListener {
            val inputText = binding.inputEditText.text.toString()
            if (inputText.isBlank()) {
                Toast.makeText(this, "텍스트를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MainScope().launch {
                binding.progressBar.visibility = android.view.View.VISIBLE
                binding.inferButton.isEnabled = false
                binding.resultTextView.text = "추론 중..."
                try {
                    val result = withContext(Dispatchers.Default) { // 추론은 Default Dispatcher에서
                        runInference(inputText)
                    }
                    binding.resultTextView.text = result
                } catch (e: Exception) {
                    Log.e("MainActivity", "추론 실패: ${e.message}", e)
                    binding.resultTextView.text = "오류: ${e.message}"
                    Toast.makeText(this@MainActivity, "추론 실패: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.inferButton.isEnabled = true
                }
            }
        }
    }

    // ONNX Runtime 초기화
    private fun initOnnxRuntime(context: Context) {
        // 1. assets에서 내부 캐시로 모델을 복사하고 파일 경로를 가져옵니다.
        val modelPath = getModelPath(context)

        ortEnv = OrtEnvironment.getEnvironment()

        // 2. ByteArray (modelBytes) 대신 파일 경로(String)를 createSession에 전달합니다.
        // 이 방식은 OOM을 유발하지 않습니다.
        ortSession = ortEnv?.createSession(modelPath, OrtSession.SessionOptions())

        ortSession?.let {
            Log.d("MainActivity", "✅ ONNX 모델 로드 성공")

            Log.d("MainActivity", "--- 모델 입력 이름 (Input Names) ---")
            it.inputNames.forEach { name -> Log.d("MainActivity", "Input: $name") }

            Log.d("MainActivity", "--- 모델 출력 이름 (Output Names) ---")
            it.outputNames.forEach { name -> Log.d("MainActivity", "Output: $name") }

        } ?: run {
            Log.e("MainActivity", "❌ ONNX 세션 생성 실패")
        }
        Log.d("MainActivity", "ONNX 모델 로드 성공. 경로: $modelPath")
    }

    /**
     * assets의 모델 파일을 앱 내부 캐시 디렉토리로 복사하고
     * 해당 파일의 절대 경로를 반환합니다.
     * 파일이 이미 존재하면 복사하지 않고 기존 경로를 반환합니다.
     */
    private fun getModelPath(context: Context): String {
        val file = File(context.cacheDir, modelName)

        // 파일이 이미 캐시에 있는지 확인
        if (file.exists()) {
            Log.d("MainActivity", "모델이 캐시에 이미 존재합니다: ${file.absolutePath}")
            return file.absolutePath
        }

        Log.d("MainActivity", "모델을 캐시로 복사합니다: ${file.absolutePath}")

        // 캐시에 파일이 없으면 assets에서 복사
        try {
            context.assets.open(modelName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "모델 파일 복사 실패", e)
            // 초기화 catch 블록에서 잡을 수 있도록 예외를 다시 던집니다.
            throw RuntimeException("모델 파일 복사 실패", e)
        }

        return file.absolutePath
    }

    // 토크나이저 초기화 (vocab.txt 로드)
    private fun initTokenizer(context: Context) {
        val reader = BufferedReader(InputStreamReader(context.assets.open("vocab.txt")))
        val tempVocab = HashMap<String, Int>()
        var line: String?
        var index = 0
        while (reader.readLine().also { line = it } != null) {
            tempVocab[line!!] = index++
        }
        vocab = tempVocab
        Log.d("MainActivity", "Vocab 로드 성공. 크기: ${vocab.size}")
    }

    // 텍스트 토큰화 (간단화된 WordPiece)
    private fun tokenize(text: String): Triple<LongArray, LongArray, LongArray> {
        val tokens = mutableListOf<String>()
        tokens.add(clsToken) // [CLS] 토큰 추가

        text.split(" ").forEach { word ->
            if (vocab.containsKey(word)) {
                tokens.add(word)
            } else {
                tokens.add(unkToken)
            }
        }
        tokens.add(sepToken) // [SEP] 토큰 추가

        // ⭐️ [LongArray]로 변경하고, 패딩 값을 0L (Long)로 지정
        val inputIds = LongArray(maxSequenceLength) { vocab[padToken]?.toLong() ?: 0L }
        val attentionMask = LongArray(maxSequenceLength) { 0L } // ⭐️ 0L
        val tokenTypeIds = LongArray(maxSequenceLength) { 0L } // ⭐️ 0L

        for (i in 0 until minOf(tokens.size, maxSequenceLength)) {
            // ⭐️ .toLong()으로 변환
            inputIds[i] = (vocab[tokens[i]] ?: vocab[unkToken] ?: 0).toLong()
            attentionMask[i] = 1L // ⭐️ 1L
        }

        // (Logcat은 옵션)
        Log.d("Tokenizer", "Tokens: $tokens")
        Log.d("Tokenizer", "Input IDs (Long): ${inputIds.take(tokens.size)}")

        // ⭐️ [LongArray] 반환
        return Triple(inputIds, attentionMask, tokenTypeIds)
    }

    // ONNX 추론 실행
    private fun runInference(text: String): String {
        if (ortEnv == null || ortSession == null || !::vocab.isInitialized) {
            throw IllegalStateException("ONNX Session or Tokenizer not initialized.")
        }

        val (inputIds, attentionMask, tokenTypeIds) = tokenize(text)

        val inputIdsBuffer = LongBuffer.wrap(inputIds)
        val attentionMaskBuffer = LongBuffer.wrap(attentionMask)

        val inputShape = longArrayOf(1, maxSequenceLength.toLong())

        val inputs = TreeMap<String, OnnxTensor>()
        inputs["input_ids"] = OnnxTensor.createTensor(ortEnv, inputIdsBuffer, inputShape)
        inputs["attention_mask"] = OnnxTensor.createTensor(ortEnv, attentionMaskBuffer, inputShape)

        var resultString = "추론 실패"

        ortSession?.use { session ->
            session.run(inputs).use { results ->

                // 1. 이름("logits")으로 찾지 않고, 결과(results)의 '첫 번째 값'을 가져옵니다.
                //    (results는 Iterable입니다)
                val firstOutput = results.firstOrNull()

                // 2. 그 첫 번째 값(OnnxValue)을 OnnxTensor로 변환합니다.
                val logitsTensor = firstOutput?.value as? OnnxTensor


                logitsTensor?.let { tensor ->
                    val logits = tensor.floatBuffer.array()

                    val predictedClass = if (logits.size > 1) {
                        // logits[0] > logits[1] 이면 "긍정"입니다.
                        if (logits[0] > logits[1]) {
                            "긍정 (Score: %.2f)".format(logits[0])
                        } else {
                            // 그 외 (logits[1]이 더 크면) "부정"입니다.
                            "부정 (Score: %.2f)".format(logits[1])
                        }
                    } else {
                        "결과 로짓: ${logits.joinToString()}"
                    }

                    resultString = "입력: '$text'\n예측: $predictedClass\n모든 로짓: ${logits.joinToString()}"
                    Log.d("MainActivity", "추론 결과: $resultString")

                } ?: run {
                    // 오류 메시지도 수정
                    resultString = "추론 실패: 결과에서 첫 번째 텐서를 가져올 수 없습니다. (bert.onnx 파일 확인)"
                    Log.e("MainActivity", "Failed to get first output or cast to OnnxTensor.")
                }
            }
        }

        return resultString
    }

    // 시그모이드 함수 (로짓을 확률로 변환하는 간단한 예시)
    private fun sigmoid(x: Float): Float {
        return 1f / (1f + kotlin.math.exp(-x))
    }

    override fun onDestroy() {
        super.onDestroy()
        ortSession?.close()
        ortEnv?.close()
    }
}