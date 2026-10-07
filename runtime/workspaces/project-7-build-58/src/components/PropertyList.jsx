import React, { useState } from 'react';
import { PropertyCard } from './PropertyCard';
import { propertiesData } from '../data/mockData';
import { Sparkles, SlidersHorizontal, Search } from 'lucide-react';

export const PropertyList = ({ onSelectProperty, savedIds, onToggleSave }) => {
  const [filterType, setFilterType] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');

  const types = ['All', 'Penthouse', 'Estate', 'Villa', 'Chalet'];

  const filteredProperties = propertiesData.filter(property => {
    const matchesType = filterType === 'All' || property.type === filterType;
    const matchesSearch = property.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          property.location.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesType && matchesSearch;
  });

  return (
    <section id="properties" className="py-24 bg-[#121212] relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-[#181715] border border-[#C5A059]/30 mb-4">
            <Sparkles className="w-4 h-4 text-[#C5A059]" />
            <span className="text-[10px] tracking-[0.3em] uppercase text-[#EAE6DF] font-semibold">
              Curated Portfolio
            </span>
          </div>
          <h2 className="font-serif text-3xl sm:text-5xl font-bold text-[#EAE6DF] mb-4">
            Featured Luxury Residences
          </h2>
          <p className="text-[#A39E93] text-sm sm:text-base font-light">
            Each Aurelia residence is rigorously vetted for uncompromising architectural integrity, premier location, and exceptional pedigree.
          </p>
        </div>

        {/* Filter Tabs & Quick Search */}
        <div className="flex flex-col lg:flex-row items-center justify-between gap-6 mb-12">
          
          {/* Category Tabs */}
          <div className="flex flex-wrap items-center justify-center gap-2 bg-[#181715] p-2 rounded-2xl border border-[#2D2A26]">
            {types.map((type) => (
              <button
                key={type}
                onClick={() => setFilterType(type)}
                className={`px-5 py-2.5 rounded-xl text-xs uppercase tracking-widest font-semibold transition-all duration-300 ${
                  filterType === type 
                    ? 'bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] shadow-lg' 
                    : 'text-[#A39E93] hover:text-[#EAE6DF]'
                }`}
              >
                {type}
              </button>
            ))}
          </div>

          {/* Quick Search Input */}
          <div className="relative w-full lg:w-80">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-[#A39E93]" />
            <input
              type="text"
              placeholder="Search city, name..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-[#181715] border border-[#2D2A26] rounded-xl pl-11 pr-4 py-3 text-xs text-[#EAE6DF] placeholder-[#A39E93] focus:outline-none focus:border-[#C5A059] transition-colors"
            />
          </div>

        </div>

        {/* Property Grid */}
        {filteredProperties.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {filteredProperties.map((property) => (
              <PropertyCard
                key={property.id}
                property={property}
                onSelect={onSelectProperty}
                onToggleSave={onToggleSave}
                isSaved={savedIds.includes(property.id)}
              />
            ))}
          </div>
        ) : (
          <div className="text-center py-20 bg-[#181715] rounded-2xl border border-[#2D2A26]">
            <p className="font-serif text-xl text-[#EAE6DF] mb-2">No matching residences found</p>
            <p className="text-xs text-[#A39E93] mb-6">Try adjusting your filter or search criteria.</p>
            <button
              onClick={() => { setFilterType('All'); setSearchQuery(''); }}
              className="px-6 py-2.5 rounded-xl bg-[#C5A059] text-[#121212] font-semibold text-xs uppercase tracking-widest"
            >
              Reset Filters
            </button>
          </div>
        )}

      </div>
    </section>
  );
};
