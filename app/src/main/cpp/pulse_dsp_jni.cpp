#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <vector>
#include "StereoDSP.hpp"

extern "C" JNIEXPORT jlong JNICALL
Java_com_pulseplayer_app_PulsePcmProcessor_nativeCreate(JNIEnv*, jobject, jdouble sampleRate) {
    try { return reinterpret_cast<jlong>(new pulse::StereoDSP(sampleRate)); }
    catch (...) { return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_pulseplayer_app_PulsePcmProcessor_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<pulse::StereoDSP*>(handle);
}
extern "C" JNIEXPORT void JNICALL
Java_com_pulseplayer_app_PulsePcmProcessor_nativeProcess(
    JNIEnv* env, jobject, jlong handle, jshortArray samples, jint frames, jfloat bass, jfloat clarity) {
    auto* dsp=reinterpret_cast<pulse::StereoDSP*>(handle);
    if(!dsp || !samples || frames<=0 || env->GetArrayLength(samples)<frames*2) return;
    try {
        dsp->setBass(100, std::clamp(static_cast<double>(bass),-6.0,6.0), 0.06);
        dsp->setClarity(5000, std::clamp(static_cast<double>(clarity),-6.0,6.0), 0.04);
        std::vector<jshort> pcm(static_cast<std::size_t>(frames)*2);
        env->GetShortArrayRegion(samples,0,frames*2,pcm.data());
        if(env->ExceptionCheck())return;
        std::vector<float> normalized(pcm.size());
        for(std::size_t i=0;i<pcm.size();++i) normalized[i]=static_cast<float>(pcm[i])/32768.0f;
        dsp->process(normalized.data(),static_cast<std::size_t>(frames));
        for(std::size_t i=0;i<pcm.size();++i) {
            auto v=std::clamp(normalized[i],-1.0f,1.0f);
            pcm[i]=static_cast<jshort>(std::clamp(static_cast<int>(v*32768.0f),-32768,32767));
        }
        env->SetShortArrayRegion(samples,0,frames*2,pcm.data());
    } catch (...) { return; }
}
