import React, { useState } from 'react';
import { MapPin, Bed, Bath, Square, ArrowRight, Heart, Sparkles } from 'lucide-react';

export const PropertyCard = ({ property, onSelect, onToggleSave, isSaved }) => {
  return (
    <div className="group bg-[#181715] border border-[#2D2A26] rounded-2xl overflow-hidden transition-all duration-500 hover:border-[#C5A059]/60 hover:shadow-[0_20px_40px_rgba(0,0,0,0.6)] flex flex-col">
      
      {/* Property Image Container */}
      <div className="relative h-72 sm:h-80 overflow-hidden cursor-pointer" onClick={() => onSelect(property)}>
        <img 
          src={property.image} 
          alt={property.title} 
          className="w-full h-full object-cover object-center transition-transform duration-700 group-hover:scale-110"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#181715] via-transparent to-black/30"></div>
        
        {/* Status Badge */}
        <div className="absolute top-4 left-4">
          <span className="px-3 py-1 rounded-full bg-[#121212]/80 backdrop-blur-md border border-[#C5A059]/50 text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold">
            {property.status}
          </span>
        </div>

        {/* Save Favorite Button */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            onToggleSave(property.id);
          }}
          className={`absolute top-4 right-4 p-2.5 rounded-full backdrop-blur-md transition-all ${
            isSaved 
              ? 'bg-[#C5A059] text-[#121212]' 
              : 'bg-[#121212]/60 text-[#EAE6DF] hover:bg-[#C5A059] hover:text-[#121212]'
          }`}
          title={isSaved ? "Remove from saved" : "Save property"}
        >
          <Heart className={`w-4 h-4 ${isSaved ? 'fill-current' : ''}`} />
        </button>

        {/* Price Tag Overlay at bottom of image */}
        <div className="absolute bottom-4 left-4 right-4 flex items-center justify-between">
          <span className="font-serif text-2xl font-bold text-[#EAE6DF] drop-shadow-md">
            {property.formattedPrice}
          </span>
          <span className="text-xs uppercase tracking-widest text-[#C5A059] bg-[#121212]/80 px-2.5 py-1 rounded-md">
            {property.type}
          </span>
        </div>
      </div>

      {/* Property Details */}
      <div className="p-6 flex-1 flex flex-col justify-between">
        <div>
          <div className="flex items-center gap-1.5 text-xs text-[#A39E93] mb-2">
            <MapPin className="w-3.5 h-3.5 text-[#C5A059]" />
            <span>{property.location}</span>
          </div>

          <h3 
            onClick={() => onSelect(property)}
            className="font-serif text-xl font-bold text-[#EAE6DF] group-hover:text-[#C5A059] transition-colors cursor-pointer mb-2 line-clamp-1"
          >
            {property.title}
          </h3>

          <p className="text-xs text-[#A39E93] line-clamp-2 mb-6 font-light">
            {property.description}
          </p>
        </div>

        <div>
          {/* Key specs */}
          <div className="grid grid-cols-3 gap-2 py-3 border-t border-b border-[#2D2A26] mb-6 text-center">
            <div className="flex flex-col items-center">
              <div className="flex items-center gap-1 text-xs text-[#EAE6DF] font-medium">
                <Bed className="w-3.5 h-3.5 text-[#C5A059]" />
                <span>{property.bedrooms} Beds</span>
              </div>
            </div>
            <div className="flex flex-col items-center border-x border-[#2D2A26]">
              <div className="flex items-center gap-1 text-xs text-[#EAE6DF] font-medium">
                <Bath className="w-3.5 h-3.5 text-[#C5A059]" />
                <span>{property.bathrooms} Baths</span>
              </div>
            </div>
            <div className="flex flex-col items-center">
              <div className="flex items-center gap-1 text-xs text-[#EAE6DF] font-medium">
                <Square className="w-3.5 h-3.5 text-[#C5A059]" />
                <span>{property.sqft.toLocaleString()} sqft</span>
              </div>
            </div>
          </div>

          {/* Action CTA */}
          <button
            onClick={() => onSelect(property)}
            className="w-full py-3 rounded-xl border border-[#C5A059]/40 bg-[#121212] hover:bg-gradient-to-r hover:from-[#C5A059] hover:to-[#A6823F] hover:text-[#121212] text-[#EAE6DF] font-semibold text-xs tracking-[0.2em] uppercase transition-all duration-300 flex items-center justify-center gap-2 group/btn"
          >
            <span>View Residence</span>
            <ArrowRight className="w-4 h-4 transition-transform group-hover/btn:translate-x-1" />
          </button>
        </div>

      </div>

    </div>
  );
};
