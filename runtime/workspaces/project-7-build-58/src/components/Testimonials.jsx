import React, { useState } from 'react';
import { testimonialsData } from '../data/mockData';
import { Quote, ChevronLeft, ChevronRight, Star, Sparkles } from 'lucide-react';

export const Testimonials = () => {
  const [currentIndex, setCurrentIndex] = useState(0);

  const handlePrev = () => {
    setCurrentIndex((prev) => (prev === 0 ? testimonialsData.length - 1 : prev - 1));
  };

  const handleNext = () => {
    setCurrentIndex((prev) => (prev === testimonialsData.length - 1 ? 0 : prev + 1));
  };

  const current = testimonialsData[currentIndex];

  return (
    <section id="testimonials" className="py-24 bg-[#181715] relative overflow-hidden">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-[#121212] border border-[#C5A059]/30 mb-4">
            <Sparkles className="w-4 h-4 text-[#C5A059]" />
            <span className="text-[10px] tracking-[0.3em] uppercase text-[#EAE6DF] font-semibold">
              Client Testimonials
            </span>
          </div>
          <h2 className="font-serif text-3xl sm:text-5xl font-bold text-[#EAE6DF] mb-4">
            Words From Our Esteemed Clients
          </h2>
          <p className="text-[#A39E93] text-sm sm:text-base font-light">
            Discretion, dedication, and peerless results. Here is what global leaders say about their experience with Aurelia Estates.
          </p>
        </div>

        {/* Testimonial Card Slider */}
        <div className="relative max-w-4xl mx-auto glass-panel p-8 sm:p-12 rounded-3xl border border-[#C5A059]/30 shadow-[0_25px_60px_rgba(0,0,0,0.8)]">
          <div className="absolute top-8 left-8 text-[#C5A059]/20">
            <Quote className="w-16 h-16" />
          </div>

          <div className="relative z-10 flex flex-col items-center text-center">
            
            {/* Stars */}
            <div className="flex items-center gap-1.5 mb-6">
              {[...Array(5)].map((_, i) => (
                <Star key={i} className="w-4 h-4 fill-[#C5A059] text-[#C5A059]" />
              ))}
            </div>

            {/* Quote */}
            <blockquote className="font-serif text-xl sm:text-2xl md:text-3xl italic text-[#EAE6DF] leading-relaxed mb-8">
              "{current.quote}"
            </blockquote>

            {/* Author Info */}
            <div className="mb-2">
              <h4 className="font-serif text-lg font-bold text-[#C5A059]">
                {current.author}
              </h4>
              <p className="text-xs uppercase tracking-widest text-[#A39E93]">
                {current.location} &bull; <span className="text-[#EAE6DF]">{current.property}</span>
              </p>
            </div>

          </div>

          {/* Controls */}
          <div className="flex items-center justify-between mt-8 pt-6 border-t border-[#2D2A26]">
            <div className="flex items-center gap-2">
              {testimonialsData.map((_, idx) => (
                <button
                  key={idx}
                  onClick={() => setCurrentIndex(idx)}
                  className={`h-1.5 rounded-full transition-all duration-300 ${
                    currentIndex === idx ? 'w-8 bg-[#C5A059]' : 'w-2 bg-[#2D2A26]'
                  }`}
                  aria-label={`Go to slide ${idx + 1}`}
                />
              ))}
            </div>

            <div className="flex items-center gap-3">
              <button
                onClick={handlePrev}
                className="p-3 rounded-full border border-[#2D2A26] bg-[#121212] hover:border-[#C5A059] text-[#EAE6DF] hover:text-[#C5A059] transition-all"
                aria-label="Previous Testimonial"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={handleNext}
                className="p-3 rounded-full border border-[#2D2A26] bg-[#121212] hover:border-[#C5A059] text-[#EAE6DF] hover:text-[#C5A059] transition-all"
                aria-label="Next Testimonial"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>

        </div>

      </div>
    </section>
  );
};
