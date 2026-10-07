import React from 'react';
import { Award, ShieldCheck, Globe, Compass, ArrowRight } from 'lucide-react';

export const AboutBrand = () => {
  return (
    <section id="about" className="py-24 bg-[#181715] relative overflow-hidden border-y border-[#2D2A26]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          
          {/* Left Visual Composition */}
          <div className="lg:col-span-6 relative">
            <div className="relative z-10 rounded-2xl overflow-hidden border border-[#C5A059]/35 shadow-[0_25px_50px_rgba(0,0,0,0.8)]">
              <img 
                src="https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1000&q=80" 
                alt="Aurelia Estates Luxury Interior" 
                className="w-full h-[500px] object-cover object-center"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-[#121212]/80 via-transparent to-transparent"></div>
              
              {/* Floating Badge */}
              <div className="absolute bottom-6 left-6 right-6 p-6 rounded-xl glass-panel border border-[#C5A059]/40 flex items-center gap-4">
                <div className="w-12 h-12 rounded-full bg-[#C5A059]/20 border border-[#C5A059] flex items-center justify-center shrink-0">
                  <Award className="w-6 h-6 text-[#C5A059]" />
                </div>
                <div>
                  <h4 className="font-serif text-lg font-bold text-[#EAE6DF]">Global Luxury Brokerage of the Year</h4>
                  <p className="text-xs text-[#A39E93]">Recognized for excellence in ultra-prime real estate advisory.</p>
                </div>
              </div>
            </div>

            {/* Decorative background accent */}
            <div className="absolute -bottom-8 -left-8 w-64 h-64 bg-[#C5A059]/10 rounded-full blur-3xl -z-0"></div>
          </div>

          {/* Right Content */}
          <div className="lg:col-span-6 text-left">
            <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-[#121212] border border-[#C5A059]/30 mb-6">
              <Compass className="w-4 h-4 text-[#C5A059]" />
              <span className="text-[10px] tracking-[0.3em] uppercase text-[#EAE6DF] font-semibold">
                The Aurelia Legacy
              </span>
            </div>

            <h2 className="font-serif text-3xl sm:text-5xl font-bold text-[#EAE6DF] leading-tight mb-6">
              Uncompromising Standards in Ultra-Prime Real Estate
            </h2>

            <p className="text-[#A39E93] text-sm sm:text-base font-light leading-relaxed mb-6">
              Founded on the principles of discretion, uncompromising pedigree, and peerless market intelligence, Aurelia Estates curates the world’s most prestigious residential assets for sovereigns, entrepreneurs, and global leaders.
            </p>

            <p className="text-[#A39E93] text-sm sm:text-base font-light leading-relaxed mb-8">
              Whether acquiring a historic Cap Ferrat waterfront domain, a modernist Bel Air architectural tour de force, or a cloud-skimming Manhattan penthouse, our advisors ensure an elite, white-glove acquisition experience.
            </p>

            {/* Key Pillars */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 mb-10">
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-[#C5A059]/10 border border-[#C5A059]/30 flex items-center justify-center shrink-0 mt-1">
                  <ShieldCheck className="w-4 h-4 text-[#C5A059]" />
                </div>
                <div>
                  <h4 className="font-serif font-bold text-[#EAE6DF] mb-1">Absolute Discretion</h4>
                  <p className="text-xs text-[#A39E93]">Strict confidentiality protocols for high-net-worth clientele.</p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-[#C5A059]/10 border border-[#C5A059]/30 flex items-center justify-center shrink-0 mt-1">
                  <Globe className="w-4 h-4 text-[#C5A059]" />
                </div>
                <div>
                  <h4 className="font-serif font-bold text-[#EAE6DF] mb-1">Global Reach</h4>
                  <p className="text-xs text-[#A39E93]">Offices in 14 world capitals and resort enclaves.</p>
                </div>
              </div>
            </div>

            <a
              href="#contact"
              className="inline-flex items-center gap-3 px-8 py-4 rounded-full bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-[0.2em] uppercase transition-all duration-300 hover:shadow-[0_0_25px_rgba(197,160,89,0.4)] hover:scale-105"
            >
              <span>Speak With An Advisor</span>
              <ArrowRight className="w-4 h-4" />
            </a>

          </div>

        </div>
      </div>
    </section>
  );
};
