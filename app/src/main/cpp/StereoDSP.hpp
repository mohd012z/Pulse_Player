#pragma once
#include <array>
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <stdexcept>

namespace pulse {
constexpr double pi = 3.14159265358979323846;
inline double dbGain(double db) { return std::pow(10.0, db / 20.0); }
inline double clamp(double x,double lo,double hi) { return std::max(lo,std::min(hi,x)); }

// Transposed direct-form II; state is per filter instance and per channel.
class Biquad {
public:
    struct Coeff { double b0=1,b1=0,b2=0,a1=0,a2=0; };
    void set(Coeff c) { c_=c; }
    void reset() { z1_=z2_=0; }
    double tick(double x) {
        double y=c_.b0*x+z1_;
        z1_=c_.b1*x-c_.a1*y+z2_;
        z2_=c_.b2*x-c_.a2*y;
        if(std::abs(z1_)<1e-24)z1_=0;
        if(std::abs(z2_)<1e-24)z2_=0;
        return y;
    }
    static Coeff peak(double sr,double hz,double q,double db) {
        validate(sr,hz,q);
        double a=std::sqrt(dbGain(clamp(db,-24,24)));
        double w=2*pi*hz/sr,sn=std::sin(w),cs=std::cos(w);
        double alpha=sn/(2*q),a0=1+alpha/a;
        return {(1+alpha*a)/a0,-2*cs/a0,(1-alpha*a)/a0,-2*cs/a0,(1-alpha/a)/a0};
    }
    static Coeff shelf(double sr,double hz,double db,bool high) {
        validate(sr,hz,0.707);
        // Public-domain RBJ audio EQ cookbook equations, shelf slope S=1.
        double a=std::sqrt(dbGain(clamp(db,-18,18)));
        double w=2*pi*hz/sr,cs=std::cos(w),sn=std::sin(w);
        double alpha=sn/std::sqrt(2.0)*std::sqrt(a+1/a);
        double root=2*std::sqrt(a)*alpha;
        double b0,b1,b2,a0,a1,a2;
        if(high) {
            b0=a*((a+1)+(a-1)*cs+root);
            b1=-2*a*((a-1)+(a+1)*cs);
            b2=a*((a+1)+(a-1)*cs-root);
            a0=(a+1)-(a-1)*cs+root;
            a1=2*((a-1)-(a+1)*cs);
            a2=(a+1)-(a-1)*cs-root;
        } else {
            b0=a*((a+1)-(a-1)*cs+root);
            b1=2*a*((a-1)-(a+1)*cs);
            b2=a*((a+1)-(a-1)*cs-root);
            a0=(a+1)+(a-1)*cs+root;
            a1=-2*((a-1)+(a+1)*cs);
            a2=(a+1)+(a-1)*cs-root;
        }
        return {b0/a0,b1/a0,b2/a0,a1/a0,a2/a0};
    }
    static Coeff lowpass(double sr,double hz,double q=0.7071067811865476) {
        validate(sr,hz,q);
        double w=2*pi*hz/sr,cs=std::cos(w),alpha=std::sin(w)/(2*q),a0=1+alpha;
        return {(1-cs)/(2*a0),(1-cs)/a0,(1-cs)/(2*a0),-2*cs/a0,(1-alpha)/a0};
    }
    static Coeff highpass(double sr,double hz,double q=0.7071067811865476) {
        validate(sr,hz,q);
        double w=2*pi*hz/sr,cs=std::cos(w),alpha=std::sin(w)/(2*q),a0=1+alpha;
        return {(1+cs)/(2*a0),-(1+cs)/a0,(1+cs)/(2*a0),-2*cs/a0,(1-alpha)/a0};
    }
private:
    static void validate(double sr,double hz,double q) {
        if(!std::isfinite(sr)||!std::isfinite(hz)||!std::isfinite(q)||sr<8000||hz<=0||hz>=sr*0.49||q<=0)
            throw std::invalid_argument("Invalid sample rate, frequency or Q");
    }
    Coeff c_{};
    double z1_=0,z2_=0;
};

class StereoDSP {
public:
    static constexpr std::size_t Bands=5;
    struct Band { double hz,q,db; bool enabled=true; };
    explicit StereoDSP(double sampleRate):sr_(sampleRate) {
        if(!std::isfinite(sr_)||sr_<8000)throw std::invalid_argument("Invalid sample rate");
        const std::array<Band,Bands> defaults{{{80,0.8,0},{250,1,0},{1000,1,0},{3500,1,0},{9000,0.8,0}}};
        for(std::size_t i=0;i<Bands;++i)setBand(i,defaults[i]);
        setClarity(5000,0,0);
        setBass(100,0,0);
        setInputDb(-6);setOutputDb(0);
    }
    void setBand(std::size_t index,Band band) {
        if(index>=Bands)throw std::out_of_range("EQ band");
        auto c=Biquad::peak(sr_,band.hz,band.q,band.enabled?band.db:0);
        bands_[index]=band;
        for(auto &channel:ch_)channel.eq[index].set(c);
    }
    Band band(std::size_t index) const { return bands_.at(index); }
    void setInputDb(double db) { inputGain_=dbGain(clamp(db,-48,12)); }
    void setOutputDb(double db) { outputGain_=dbGain(clamp(db,-48,12)); }
    void setClarity(double shelfHz,double shelfDb,double amount) {
        auto shelf=Biquad::shelf(sr_,shelfHz,shelfDb,true);
        auto hp=Biquad::highpass(sr_,3000);
        auto lp=Biquad::lowpass(sr_,std::min(10000.0,sr_*0.45));
        clarityAmount_=clamp(amount,0,0.25);
        for(auto &c:ch_){c.clarityShelf.set(shelf);c.clarityHP.set(hp);c.clarityLP.set(lp);}
    }
    void setBass(double shelfHz,double shelfDb,double amount) {
        auto shelf=Biquad::shelf(sr_,shelfHz,shelfDb,false);
        auto lp=Biquad::lowpass(sr_,std::min(160.0,sr_*0.25));
        bassAmount_=clamp(amount,0,0.3);
        for(auto &c:ch_){c.bassShelf.set(shelf);c.bassLP.set(lp);}
    }
    void reset(){for(auto &c:ch_)c.reset();}
    // Interleaved float PCM [L0,R0,L1,R1,...]; no allocations or channel mixing.
    void process(float* pcm,std::size_t frames) {
        if(!pcm&&frames)throw std::invalid_argument("Null buffer");
        for(std::size_t n=0;n<frames;++n)for(std::size_t channel=0;channel<2;++channel){
            auto &state=ch_[channel];
            double x=std::isfinite(pcm[2*n+channel])?pcm[2*n+channel]:0;
            x*=inputGain_;
            for(auto &eq:state.eq)x=eq.tick(x);
            x=state.clarityShelf.tick(x);
            double presence=state.clarityLP.tick(state.clarityHP.tick(x));
            x+=clarityAmount_*(std::tanh(1.5*presence)-presence*std::tanh(1.5));
            x=state.bassShelf.tick(x);
            double low=state.bassLP.tick(x);
            x+=bassAmount_*(std::tanh(2*low)-low*std::tanh(2.0));
            x*=outputGain_;
            pcm[2*n+channel]=static_cast<float>(clamp(x,-1,1));
        }
    }
private:
    struct Channel {
        std::array<Biquad,Bands> eq;
        Biquad clarityShelf,clarityHP,clarityLP,bassShelf,bassLP;
        void reset(){for(auto &f:eq)f.reset();clarityShelf.reset();clarityHP.reset();clarityLP.reset();bassShelf.reset();bassLP.reset();}
    };
    double sr_,inputGain_=1,outputGain_=1,clarityAmount_=0,bassAmount_=0;
    std::array<Band,Bands> bands_{};
    std::array<Channel,2> ch_{};
};
} // namespace pulse
