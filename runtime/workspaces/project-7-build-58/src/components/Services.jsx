import React from 'react';
import { servicesData } from '../data/mockData';
import { Crown, ShieldCheck, Sparkles, Key, ArrowRight } from 'lucide-react';

const iconMap = {
  Crown: Crown,
  ShieldCheck: ShieldCheck,
  Sparkles: Sparkles,
  Key: Key
};

export const Services = ({ onOpenInquiry }) => {
  return (
    <section id="services" className="py-24 bg-[#121212] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-[#181715] border border-[#C5A059]/30 mb-4">
            <Sparkles className="w-4 h-4 text-[#C5A059]" />
            <span className="text-[10px] tracking-[0.3em] uppercase text-[#EAE6DF] font-semibold">
              Bespoke Services & Advantages
            </span>
          </div>
          <h2 className="font-serif text-3xl sm:text-5xl font-bold text-[#EAE6DF] mb-4">
            Tailored Advisory & Wealth Solutions
          </h2>
          <p className="text-[#A39E93] text-sm sm:text-base font-light">
            Beyond property transactions, we provide comprehensive private wealth management, architecture curation, and global concierge services.
          </p>
        </div>

        {/* Services Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {servicesData.map((service, index) => {
            const IconComponent = iconMap[service.icon] || Crown;
            return (
              <div 
                key={index}
                className="group bg-[#181715] border border-[#2D2A26] p-8 sm:p-10 rounded-2xl transition-all duration-500 hover:border-[#C5A059]/60 hover:shadow-[0_20px_40px_rgba(0,0,0,0.6)] flex flex-col justify-between"
              >
                <div>
                  <div className="w-14 h-14 rounded-2xl bg-[#121212] border border-[#C5A059]/40 flex items-center justify-center mb-6 group-hover:bg-[#C5A059] group-hover:text-[#121212] transition-colors duration-300">
                    <IconComponent className="w-7 h-7 text-[#C5A059] group-hover:text-[#121212]" />
                  </div>

                  <h3 className="font-serif text-2xl font-bold text-[#EAE6DF] mb-3 group-hover:text-[#C5A059] transition-colors">
                    {service.title}
                  </h3>

                  <p className="text-sm text-[#A39E93] font-light leading-relaxed mb-6">
                    {service.description}
                  </p>
                </div>

                <button
                  onClick={onOpenInquiry}
                  className="inline-flex items-center gap-2 text-xs uppercase tracking-widest text-[#C5A059] font-semibold group-hover:gap-3 transition-all"
                >
                  <span>Learn More</span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            );
          })}
        </div>

      </div>
    </section>
  );
};
