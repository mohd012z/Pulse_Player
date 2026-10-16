#include "StereoDSP.hpp"
#include <cassert>
#include <cmath>
#include <vector>
int main() {
    pulse::StereoDSP dsp(48000.0);
    dsp.setBand(0,{80.0,0.8,3.0});
    dsp.setBand(1,{250.0,1.0,-2.0});
    assert(dsp.band(0).db==3.0);
    assert(dsp.band(1).db==-2.0);
    dsp.setClarity(5000,2.0,0.05);
    dsp.setBass(100,3.0,0.08);
    std::vector<float> pcm(48000*2,0.0f);
    pcm[0]=0.1f;
    dsp.process(pcm.data(),48000);
    for(std::size_t i=0;i<48000;++i) {
        assert(std::isfinite(pcm[2*i]));
        assert(std::isfinite(pcm[2*i+1]));
        assert(pcm[2*i+1]==0.0f); // no L->R leakage
    }
    dsp.reset();
    return 0;
}
