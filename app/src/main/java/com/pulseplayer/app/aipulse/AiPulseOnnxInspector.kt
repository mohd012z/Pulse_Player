package com.pulseplayer.app.aipulse

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * P0: Inspect actual ONNX graph signatures before attempting synthesis.
 * Only the exact, verified minimal renderer profile is currently supported.
 */
internal object AiPulseOnnxInspector {
    private val acousticProfile = setOf("tokens", "durations", "f0", "speedup")
    private val vocoderProfile = setOf("mel", "f0")

    fun inspect(pack: AiPulseModelPack): JSONObject {
        val env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            env.createSession(pack.acoustic.absolutePath, options).use { acoustic ->
                env.createSession(pack.vocoder.absolutePath, options).use { vocoder ->
                    val compatible = acoustic.inputNames == acousticProfile &&
                        vocoder.inputNames == vocoderProfile &&
                        acoustic.outputNames.size == 1 &&
                        vocoder.outputNames.size == 1
                    return JSONObject().apply {
                        put("voice", pack.name)
                        put("sample_rate", pack.sampleRate)
                        put("frame_rate", pack.frameRate)
                        put("renderer_profile", "minimal-diffsinger-v1")
                        put("supported_signature", compatible)
                        put("acoustic", describe(acoustic))
                        put("vocoder", describe(vocoder))
                        put("note", if (compatible)
                            "Names match minimal profile; tensor shapes and audio still require inference validation."
                            else "Unsupported ONNX signature; do not run this renderer.")
                    }
                }
            }
        }
    }

    private fun describe(session: OrtSession): JSONObject {
        fun nodes(info: Map<String, ai.onnxruntime.NodeInfo>): JSONArray {
            val result = JSONArray()
            info.forEach { (name, node) ->
                val entry = JSONObject().put("name", name)
                val tensor = node.info as? TensorInfo
                if (tensor != null) {
                    entry.put("shape", JSONArray(tensor.shape.toList()))
                    entry.put("type", tensor.type.toString())
                } else {
                    entry.put("type", node.info.toString())
                }
                result.put(entry)
            }
            return result
        }
        return JSONObject()
            .put("inputs", nodes(session.inputInfo))
            .put("outputs", nodes(session.outputInfo))
    }
}
