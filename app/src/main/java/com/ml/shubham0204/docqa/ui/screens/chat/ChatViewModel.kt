import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

fun getAnswer(
    query: String,
    prompt: String,
) {
    val apiKey = geminiAPIKey.getAPIKey() ?: throw Exception("Gemini API key is null")
    val geminiRemoteAPI = GeminiRemoteAPI(apiKey)
    _isGeneratingResponseState.value = true
    _questionState.value = query
    try {
        var jointContext = ""
        val retrievedContextList = ArrayList<RetrievedContext>()
        val queryEmbedding = sentenceEncoder.encodeText(query)
        chunksDB.getSimilarChunks(queryEmbedding, n = 5).forEach {
            jointContext += " " + it.second.chunkData
            retrievedContextList.add(RetrievedContext(it.second.docFileName, it.second.chunkData))
        }
        val inputPrompt = prompt.replace("\$CONTEXT", jointContext).replace("\$QUERY", query)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 设置超时时间为10秒
                val response = withTimeout(10000) {
                    geminiRemoteAPI.getResponse(inputPrompt)
                }
                response?.let { llmResponse ->
                    _responseState.value = llmResponse
                    _isGeneratingResponseState.value = false
                    _retrievedContextListState.value = retrievedContextList
                }
            } catch (e: TimeoutCancellationException) {
                // 超时后的处理逻辑
                _isGeneratingResponseState.value = false
                _responseState.value = "请求超时，请稍后再试"
                // 可以在这里执行其他超时后的操作
            } catch (e: Exception) {
                _isGeneratingResponseState.value = false
                _responseState.value = "发生错误: ${e.message}"
            }
        }
    } catch (e: Exception) {
        _isGeneratingResponseState.value = false
        _questionState.value = ""
        throw e
    }
}