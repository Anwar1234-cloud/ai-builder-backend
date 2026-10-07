import React from 'react';
import { ArrowRight, Shield, Phone, Mail } from 'lucide-react';

export const CallToAction = ({ onOpenInquiry }) => {
  return (
    <section className="py-24 bg-[#121212] relative overflow-hidden">
      
      {/* Background Banner with Luxury Overlay */}
      <div className="absolute inset-0 z-0">
        <img 
          src="https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=2000&q=80" 
          alt="Aurelia Estate Luxury" 
          className="w-full h-full object-cover object-center opacity-25"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-[#121212] via-[#121212]/90 to-[#121212]"></div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center">
        <div className="glass-panel p-10 sm:p-16 rounded-3xl border border-[#C5A059]/40 shadow-[0_30px_70px_rgba(0,0,0,0.9)]">
          
          <div className="w-14 h-14 rounded-2xl bg-[#C5A059]/20 border border-[#C5A059] flex items-center justify-center mx-auto mb-6">
            <Shield className="w-7 h-7 text-[#C5A059]" />
          </div>

          <h2 className="font-serif text-3xl sm:text-5xl font-bold text-[#EAE6DF] mb-6">
            Begin Your Private Estate Search
          </h2>

          <p className="text-sm sm:text-base text-[#A39E93] max-w-2xl mx-auto font-light leading-relaxed mb-10">
            Connect with our senior private wealth advisors today for confidential access to our off-market portfolio of global trophy properties.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <button
              onClick={onOpenInquiry}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-3 px-8 py-4 rounded-full bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-[0.2em] uppercase transition-all duration-300 hover:shadow-[0_0_30px_rgba(197,160,89,0.5)] hover:scale-105"
            >
              <span>Schedule Private Consultation</span>
              <ArrowRight className="w-4 h-4" />
            </button>
            <a
              href="tel:+18005550199"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-8 py-4 rounded-full border border-[#2D2A26] bg-[#181715] hover:border-[#C5A059] text-[#EAE6DF] hover:text-[#C5A059] font-medium text-xs tracking-[0.2em] uppercase transition-all"
            >
              <Phone className="w-4 h-4 text-[#C5A059]" />
              <span>Direct Concierge</span>
            </a>
          </div>

        </div>
      </div>
    </section>
  );
};
