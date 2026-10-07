import React, { useState } from 'react';
import { Search, MapPin, SlidersHorizontal, Sparkles, ArrowRight, Shield } from 'lucide-react';

export const Hero = ({ onSearch, onExploreClick }) => {
  const [location, setLocation] = useState('All Locations');
  const [type, setType] = useState('All Types');
  const [priceRange, setPriceRange] = useState('Any Price');

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    onSearch({ location, type, priceRange });
    const section = document.getElementById('properties');
    if (section) {
      section.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <section className="relative min-h-screen flex items-center justify-center pt-28 pb-20 overflow-hidden">
      {/* Background Image with Luxury Overlay and Vignette */}
      <div className="absolute inset-0 z-0">
        <img 
          src="https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=2000&q=90" 
          alt="Luxury Mansion Aurelia Estates" 
          className="w-full h-full object-cover object-center scale-105 transition-transform duration-1000 ease-out"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#121212] via-[#121212]/75 to-[#121212]/40"></div>
        <div className="absolute inset-0 bg-gradient-to-r from-[#121212]/90 via-transparent to-[#121212]/90"></div>
      </div>

      {/* Hero Content */}
      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 w-full text-center py-12">
        
        {/* Luxury Badge */}
        <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full bg-[#181715]/90 border border-[#C5A059]/40 backdrop-blur-md mb-8 shadow-[0_0_25px_rgba(197,160,89,0.2)]">
          <Sparkles className="w-4 h-4 text-[#C5A059]" />
          <span className="text-[11px] tracking-[0.3em] uppercase text-[#EAE6DF] font-semibold">
            World-Class Ultra-Prime Real Estate
          </span>
        </div>

        {/* Headline */}
        <h1 className="font-serif text-4xl sm:text-6xl md:text-7xl lg:text-8xl font-bold tracking-tight text-[#EAE6DF] max-w-5xl mx-auto leading-[1.08] mb-6 drop-shadow-md">
          Architectural Masterpieces <br className="hidden sm:inline" />
          <span className="gold-gradient-text italic font-normal">For The Discerning Few</span>
        </h1>

        {/* Supporting Text */}
        <p className="text-base sm:text-lg md:text-xl text-[#B8B2A6] max-w-2xl mx-auto font-light leading-relaxed mb-12">
          Discover private islands, cliffside villas, and sky-high penthouses across the world's most coveted metropolitan and resort destinations.
        </p>

        {/* Primary & Secondary CTAs */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-16">
          <a
            href="#properties"
            onClick={onExploreClick}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-3 px-8 py-4 rounded-full bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-bold text-xs tracking-[0.2em] uppercase transition-all duration-300 hover:shadow-[0_0_35px_rgba(197,160,89,0.6)] hover:scale-105"
          >
            <span>Explore Portfolio</span>
            <ArrowRight className="w-4 h-4" />
          </a>
          <a
            href="#about"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-8 py-4 rounded-full glass-panel text-[#EAE6DF] hover:text-[#C5A059] font-medium text-xs tracking-[0.2em] uppercase transition-all duration-300 hover:border-[#C5A059]/80"
          >
            <span>The Aurelia Legacy</span>
          </a>
        </div>

        {/* Property Search / Filter Bar Integrated Into Hero */}
        <div className="max-w-4xl mx-auto glass-panel p-4 sm:p-6 rounded-2xl shadow-[0_25px_60px_rgba(0,0,0,0.8)] border border-[#C5A059]/30">
          <form onSubmit={handleSearchSubmit} className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-4 gap-4 items-center">
            
            {/* Location Select */}
            <div className="text-left bg-[#121212]/80 border border-[#2D2A26] rounded-xl p-3.5 hover:border-[#C5A059]/60 transition-colors">
              <label className="block text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold mb-1">
                Location
              </label>
              <div className="flex items-center gap-2">
                <MapPin className="w-4 h-4 text-[#C5A059] shrink-0" />
                <select 
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  className="w-full bg-transparent text-sm text-[#EAE6DF] font-medium focus:outline-none cursor-pointer appearance-none"
                >
                  <option value="All Locations" className="bg-[#181715]">All Locations</option>
                  <option value="Miami, FL" className="bg-[#181715]">Miami, FL</option>
                  <option value="Los Angeles, CA" className="bg-[#181715]">Los Angeles, CA</option>
                  <option value="Aspen, CO" className="bg-[#181715]">Aspen, CO</option>
                  <option value="New York, NY" className="bg-[#181715]">New York, NY</option>
                  <option value="Dubai, UAE" className="bg-[#181715]">Dubai, UAE</option>
                  <option value="French Riviera" className="bg-[#181715]">French Riviera</option>
                </select>
              </div>
            </div>

            {/* Property Type Select */}
            <div className="text-left bg-[#121212]/80 border border-[#2D2A26] rounded-xl p-3.5 hover:border-[#C5A059]/60 transition-colors">
              <label className="block text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold mb-1">
                Property Type
              </label>
              <div className="flex items-center gap-2">
                <SlidersHorizontal className="w-4 h-4 text-[#C5A059] shrink-0" />
                <select 
                  value={type}
                  onChange={(e) => setType(e.target.value)}
                  className="w-full bg-transparent text-sm text-[#EAE6DF] font-medium focus:outline-none cursor-pointer appearance-none"
                >
                  <option value="All Types" className="bg-[#181715]">All Types</option>
                  <option value="Penthouse" className="bg-[#181715]">Penthouse</option>
                  <option value="Estate" className="bg-[#181715]">Estate</option>
                  <option value="Villa" className="bg-[#181715]">Villa</option>
                  <option value="Chalet" className="bg-[#181715]">Chalet</option>
                </select>
              </div>
            </div>

            {/* Price Range Select */}
            <div className="text-left bg-[#121212]/80 border border-[#2D2A26] rounded-xl p-3.5 hover:border-[#C5A059]/60 transition-colors">
              <label className="block text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold mb-1">
                Valuation
              </label>
              <div className="flex items-center gap-2">
                <Shield className="w-4 h-4 text-[#C5A059] shrink-0" />
                <select 
                  value={priceRange}
                  onChange={(e) => setPriceRange(e.target.value)}
                  className="w-full bg-transparent text-sm text-[#EAE6DF] font-medium focus:outline-none cursor-pointer appearance-none"
                >
                  <option value="Any Price" className="bg-[#181715]">Any Price</option>
                  <option value="15M+" className="bg-[#181715]">$15M - $25M</option>
                  <option value="25M+" className="bg-[#181715]">$25M - $40M</option>
                  <option value="40M+" className="bg-[#181715]">$40M+ Ultra-Prime</option>
                </select>
              </div>
            </div>

            {/* Search Button */}
            <div className="sm:col-span-3 lg:col-span-1">
              <button
                type="submit"
                className="w-full h-full min-h-[56px] rounded-xl bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-bold text-xs tracking-[0.2em] uppercase flex items-center justify-center gap-2 transition-all duration-300 hover:shadow-[0_0_25px_rgba(197,160,89,0.6)] hover:scale-[1.02]"
              >
                <Search className="w-4 h-4" />
                <span>Search</span>
              </button>
            </div>

          </form>
        </div>

      </div>
    </section>
  );
};
