import React, { useState } from 'react';
import { X, CheckCircle2, Shield, Phone, Mail } from 'lucide-react';

export const InquiryModal = ({ onClose }) => {
  const [submitted, setSubmitted] = useState(false);
  const [formData, setFormData] = useState({ name: '', email: '', phone: '', propertyType: 'Penthouse', budget: '$20M - $35M', message: '' });

  const handleSubmit = (e) => {
    e.preventDefault();
    setSubmitted(true);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
      <div className="relative w-full max-w-2xl bg-[#181715] border border-[#C5A059]/40 rounded-3xl shadow-[0_25px_80px_rgba(0,0,0,0.9)] p-8 sm:p-10">
        
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 z-20 p-2.5 rounded-full bg-[#121212]/80 border border-[#2D2A26] text-[#EAE6DF] hover:text-[#C5A059] hover:border-[#C5A059] transition-all"
        >
          <X className="w-5 h-5" />
        </button>

        {submitted ? (
          <div className="text-center py-8">
            <div className="w-16 h-16 rounded-full bg-[#C5A059]/20 border border-[#C5A059] flex items-center justify-center mx-auto mb-6">
              <CheckCircle2 className="w-8 h-8 text-[#C5A059]" />
            </div>
            <h3 className="font-serif text-2xl font-bold text-[#EAE6DF] mb-3">Consultation Request Received</h3>
            <p className="text-sm text-[#A39E93] max-w-md mx-auto mb-8 font-light">
              A senior private wealth partner at Aurelia Estates will review your criteria and contact you within 2 hours with absolute discretion.
            </p>
            <button
              onClick={onClose}
              className="px-8 py-3 rounded-xl bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-widest uppercase"
            >
              Return to Website
            </button>
          </div>
        ) : (
          <div>
            <div className="text-center mb-8">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#121212] border border-[#C5A059]/30 mb-3">
                <Shield className="w-3.5 h-3.5 text-[#C5A059]" />
                <span className="text-[9px] tracking-[0.3em] uppercase text-[#EAE6DF] font-semibold">
                  Confidential Advisory
                </span>
              </div>
              <h3 className="font-serif text-2xl sm:text-3xl font-bold text-[#EAE6DF]">
                Schedule Private Consultation
              </h3>
              <p className="text-xs text-[#A39E93] mt-2">
                Connect with our elite acquisition specialists.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Full Name</label>
                  <input
                    type="text"
                    required
                    placeholder="Lord / Lady / Mr. Name"
                    value={formData.name}
                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                    className="w-full bg-[#121212] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
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
                    className="w-full bg-[#121212] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Phone Number</label>
                  <input
                    type="tel"
                    required
                    placeholder="+1 (555) 000-0000"
                    value={formData.phone}
                    onChange={(e) => setFormData({...formData, phone: e.target.value})}
                    className="w-full bg-[#121212] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
                  />
                </div>
                <div>
                  <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Preferred Asset Type</label>
                  <select
                    value={formData.propertyType}
                    onChange={(e) => setFormData({...formData, propertyType: e.target.value})}
                    className="w-full bg-[#121212] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059] cursor-pointer"
                  >
                    <option value="Penthouse">Penthouse</option>
                    <option value="Estate">Trophy Estate</option>
                    <option value="Villa">Waterfront Villa</option>
                    <option value="Chalet">Alpine Chalet</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Acquisition Budget</label>
                <select
                  value={formData.budget}
                  onChange={(e) => setFormData({...formData, budget: e.target.value})}
                  className="w-full bg-[#121212] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059] cursor-pointer"
                >
                  <option value="$15M - $25M">$15M - $25M</option>
                  <option value="$25M - $40M">$25M - $40M</option>
                  <option value="$40M+">$40M+ Ultra-Prime</option>
                </select>
              </div>

              <div>
                <label className="block text-[10px] uppercase tracking-widest text-[#A39E93] mb-1">Specific Requirements / Locations</label>
                <textarea
                  rows="3"
                  placeholder="Mention desired locations, architectural preferences or off-market inquiries..."
                  value={formData.message}
                  onChange={(e) => setFormData({...formData, message: e.target.value})}
                  className="w-full bg-[#121212] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] focus:outline-none focus:border-[#C5A059]"
                ></textarea>
              </div>

              <button
                type="submit"
                className="w-full py-3.5 rounded-xl bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-widest uppercase transition-all shadow-lg hover:shadow-[0_0_20px_rgba(197,160,89,0.5)] mt-2"
              >
                Request Private Consultation
              </button>
            </form>
          </div>
        )}

      </div>
    </div>
  );
};
