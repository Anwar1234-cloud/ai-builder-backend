import React from 'react';
import { statisticsData } from '../data/mockData';

export const Statistics = () => {
  return (
    <section className="py-20 bg-gradient-to-b from-[#181715] to-[#121212] border-y border-[#2D2A26] relative overflow-hidden">
      
      {/* Background ambient glow */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(197,160,89,0.06)_0%,transparent_70%)]"></div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-8 md:gap-12">
          {statisticsData.map((stat, index) => (
            <div 
              key={index}
              className="text-center p-6 rounded-2xl bg-[#121212]/60 border border-[#2D2A26] backdrop-blur-md hover:border-[#C5A059]/40 transition-colors"
            >
              <div className="font-serif text-4xl sm:text-5xl md:text-6xl font-bold gold-gradient-text mb-2">
                {stat.value}
              </div>
              <div className="text-xs sm:text-sm uppercase tracking-[0.2em] text-[#A39E93] font-medium">
                {stat.label}
              </div>
            </div>
          ))}
        </div>
      </div>

    </section>
  );
};
