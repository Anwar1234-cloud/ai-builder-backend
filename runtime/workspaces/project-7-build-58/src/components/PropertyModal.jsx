import React, { useState } from 'react';
import { X, MapPin, Bed, Bath, Square, Phone, Mail, CheckCircle2, Shield, User } from 'lucide-react';

export const PropertyModal = ({ property, onClose, onOpenInquiry }) => {
  const [activeImage, setActiveImage] = useState(property.image);
  const [inquirySent, setInquirySent] = useState(false);
  const [formData, setFormData] = useState({ name: '', email: '', phone: '', message: '' });

  const handleFormSubmit = (e) => {
    e.preventDefault();
    setInquirySent(true);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/80 backdrop-blur-md overflow-y-auto animate-fadeIn">
      <div className="relative w-full max-w-5xl bg-[#181715] border border-[#C5A059]/40 rounded-3xl shadow-[0_25px_80px_rgba(0,0,0,0.9)] overflow-hidden my-8">
        
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 z-20 p-2.5 rounded-full bg-[#121212]/80 border border-[#2D2A26] text-[#EAE6DF] hover:text-[#C5A059] hover:border-[#C5A059] transition-all"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="max-h-[90vh] overflow-y-auto p-6 sm:p-10">
          
          {/* Header Bar */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
            <div>
              <div className="flex items-center gap-2 mb-2">
                <span className="px-3 py-1 rounded-full bg-[#121212] border border-[#C5A059]/40 text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold">
                  {property.status}
                </span>
                <span className="text-xs text-[#A39E93] flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5 text-[#C5A059]" /> {property.location}
                </span>
              </div>
              <h2 className="font-serif text-2xl sm:text-4xl font-bold text-[#EAE6DF]">
                {property.title}
              </h2>
            </div>
            <div className="text-left md:text-right">
              <div className="font-serif text-3xl font-bold gold-gradient-text">
                {property.formattedPrice}
              </div>
              <span className="text-xs text-[#A39E93] uppercase tracking-widest">
                {property.type} Residence
              </span>
            </div>
          </div>

          {/* Gallery View */}
          <div className="mb-8">
            <div className="rounded-2xl overflow-hidden h-[350px] sm:h-[450px] mb-4 border border-[#2D2A26]">
              <img 
                src={activeImage} 
                alt={property.title} 
                className="w-full h-full object-cover object-center"
              />
            </div>
            {property.gallery && property.gallery.length > 1 && (
              <div className="flex gap-3 overflow-x-auto pb-2">
                {property.gallery.map((img, idx) => (
                  <button
                    key={idx}
                    onClick={() => setActiveImage(img)}
                    className={`w-24 h-16 rounded-xl overflow-hidden shrink-0 border-2 transition-all ${
                      activeImage === img ? 'border-[#C5A059]' : 'border-transparent opacity-60 hover:opacity-100'
                    }`}
                  >
                    <img src={img} alt="Thumbnail" className="w-full h-full object-cover" />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Main Content Split */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
            
            {/* Left Column: Specs & Description */}
            <div className="lg:col-span-7">
              
              {/* Specs Grid */}
              <div className="grid grid-cols-3 gap-4 p-5 rounded-2xl bg-[#121212] border border-[#2D2A26] mb-8 text-center">
                <div className="flex flex-col items-center">
                  <Bed className="w-5 h-5 text-[#C5A059] mb-1" />
                  <span className="text-xs text-[#A39E93] uppercase">Bedrooms</span>
                  <span className="font-serif text-lg font-bold text-[#EAE6DF]">{property.bedrooms}</span>
                </div>
                <div className="flex flex-col items-center border-x border-[#2D2A26]">
                  <Bath className="w-5 h-5 text-[#C5A059] mb-1" />
                  <span className="text-xs text-[#A39E93] uppercase">Bathrooms</span>
                  <span className="font-serif text-lg font-bold text-[#EAE6DF]">{property.bathrooms}</span>
                </div>
                <div className="flex flex-col items-center">
                  <Square className="w-5 h-5 text-[#C5A059] mb-1" />
                  <span className="text-xs text-[#A39E93] uppercase">Interior</span>
                  <span className="font-serif text-lg font-bold text-[#EAE6DF]">{property.sqft.toLocaleString()} sqft</span>
                </div>
              </div>

              {/* Description */}
              <div className="mb-8">
                <h3 className="font-serif text-xl font-bold text-[#EAE6DF] mb-3">About The Residence</h3>
                <p className="text-sm text-[#A39E93] font-light leading-relaxed">
                  {property.description}
                </p>
              </div>

              {/* Features & Amenities */}
              {property.features && (
                <div className="mb-8">
                  <h3 className="font-serif text-xl font-bold text-[#EAE6DF] mb-4">Elite Amenities</h3>
                  <div className="grid grid-cols-2 gap-3">
                    {property.features.map((feature, idx) => (
                      <div key={idx} className="flex items-center gap-2 text-xs text-[#EAE6DF] bg-[#121212] p-3 rounded-xl border border-[#2D2A26]">
                        <Shield className="w-3.5 h-3.5 text-[#C5A059] shrink-0" />
                        <span>{feature}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

            </div>

            {/* Right Column: Agent & Inquiry Form */}
            <div className="lg:col-span-5">
              <div className="bg-[#121212] p-6 sm:p-8 rounded-2xl border border-[#C5A059]/30">
                
                {/* Agent Card */}
                {property.agent && (
                  <div className="flex items-center gap-4 pb-6 mb-6 border-b border-[#2D2A26]">
                    <img 
                      src={property.agent.image} 
                      alt={property.agent.name} 
                      className="w-14 h-14 rounded-full object-cover border border-[#C5A059]"
                    />
                    <div>
                      <span className="text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold">Listing Advisor</span>
                      <h4 className="font-serif text-base font-bold text-[#EAE6DF]">{property.agent.name}</h4>
                      <p className="text-xs text-[#A39E93]">{property.agent.title}</p>
                    </div>
                  </div>
                )}

                <h3 className="font-serif text-lg font-bold text-[#EAE6DF] mb-4">Request Private Viewing</h3>

                {inquirySent ? (
                  <div className="p-6 rounded-xl bg-[#C5A059]/10 border border-[#C5A059]/40 text-center">
                    <CheckCircle2 className="w-10 h-10 text-[#C5A059] mx-auto mb-3" />
                    <h4 className="font-serif text-lg font-bold text-[#EAE6DF] mb-1">Inquiry Confirmed</h4>
                    <p className="text-xs text-[#A39E93] mb-4">
                      {property.agent?.name} or our concierge team will reach out to you within 2 hours.
                    </p>
                    <button
                      onClick={() => setInquirySent(false)}
                      className="px-4 py-2 rounded-lg bg-[#C5A059] text-[#121212] font-semibold text-xs uppercase"
                    >
                      Send Another Inquiry
                    </button>
                  </div>
                ) : (
                  <form onSubmit={handleFormSubmit} className="flex flex-col gap-4">
                    <div>
                      <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Full Name</label>
                      <input 
                        type="text" 
                        required
                        placeholder="Lord / Lady / Mr. Name"
                        value={formData.name}
                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                        className="w-full bg-[#181715] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Email Address</label>
                      <input 
                        type="email" 
                        required
                        placeholder="client@domain.com"
                        value={formData.email}
                        onChange={(e) => setFormData({...formData, email: e.target.value})}
                        className="w-full bg-[#181715] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Phone Number</label>
                      <input 
                        type="tel" 
                        required
                        placeholder="+1 (555) 000-0000"
                        value={formData.phone}
                        onChange={(e) => setFormData({...formData, phone: e.target.value})}
                        className="w-full bg-[#181715] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Preferred Date / Notes</label>
                      <textarea 
                        rows="3"
                        placeholder="Request private tour or specific requirements..."
                        value={formData.message}
                        onChange={(e) => setFormData({...formData, message: e.target.value})}
                        className="w-full bg-[#181715] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
                      ></textarea>
                    </div>

                    <button
                      type="submit"
                      className="w-full py-3 rounded-xl bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-widest uppercase transition-all shadow-lg hover:shadow-[0_0_20px_rgba(197,160,89,0.5)]"
                    >
                      Submit Private Inquiry
                    </button>
                  </form>
                )}

              </div>
            </div>

          </div>

        </div>

      </div>
    </div>
  );
};
