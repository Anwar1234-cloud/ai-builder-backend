import React, { useState } from 'react';
import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { PropertyList } from './components/PropertyList';
import { AboutBrand } from './components/AboutBrand';
import { Services } from './components/Services';
import { Statistics } from './components/Statistics';
import { Testimonials } from './components/Testimonials';
import { CallToAction } from './components/CallToAction';
import { Footer } from './components/Footer';
import { PropertyModal } from './components/PropertyModal';
import { InquiryModal } from './components/InquiryModal';
import { propertiesData } from './data/mockData';
import { X, Trash2, ArrowRight } from 'lucide-react';

export default function App() {
  const [selectedProperty, setSelectedProperty] = useState(null);
  const [inquiryModalOpen, setInquiryModalOpen] = useState(false);
  const [savedModalOpen, setSavedModalOpen] = useState(false);
  const [savedIds, setSavedIds] = useState([1, 3]);

  const handleToggleSave = (id) => {
    if (savedIds.includes(id)) {
      setSavedIds(savedIds.filter(item => item !== id));
    } else {
      setSavedIds([...savedIds, id]);
    }
  };

  const savedProperties = propertiesData.filter(p => savedIds.includes(p.id));

  return (
    <div className="min-h-screen bg-[#121212] text-[#EAE6DF] selection:bg-[#C5A059] selection:text-[#121212]">
      
      {/* Navigation */}
      <Navbar 
        onOpenInquiry={() => setInquiryModalOpen(true)}
        onOpenSaved={() => setSavedModalOpen(true)}
        savedCount={savedIds.length}
      />

      {/* Hero Section with Integrated Search */}
      <Hero 
        onSearch={(filters) => {
          console.log('Search filters:', filters);
        }}
        onExploreClick={() => {
          const el = document.getElementById('properties');
          if (el) el.scrollIntoView({ behavior: 'smooth' });
        }}
      />

      {/* Featured Properties Section */}
      <PropertyList 
        onSelectProperty={(property) => setSelectedProperty(property)}
        savedIds={savedIds}
        onToggleSave={handleToggleSave}
      />

      {/* About the Brand Section */}
      <AboutBrand />

      {/* Services / Advantages Section */}
      <Services onOpenInquiry={() => setInquiryModalOpen(true)} />

      {/* Large Visual Statistics Section */}
      <Statistics />

      {/* Customer Testimonials */}
      <Testimonials />

      {/* Premium Call-To-Action Section */}
      <CallToAction onOpenInquiry={() => setInquiryModalOpen(true)} />

      {/* Contact & Footer Section */}
      <Footer />

      {/* Property Detail Modal */}
      {selectedProperty && (
        <PropertyModal 
          property={selectedProperty}
          onClose={() => setSelectedProperty(null)}
          onOpenInquiry={() => {
            setSelectedProperty(null);
            setInquiryModalOpen(true);
          }}
        />
      )}

      {/* General Private Consultation Inquiry Modal */}
      {inquiryModalOpen && (
        <InquiryModal 
          onClose={() => setInquiryModalOpen(false)}
        />
      )}

      {/* Saved Properties Drawer / Modal */}
      {savedModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
          <div className="relative w-full max-w-3xl bg-[#181715] border border-[#C5A059]/40 rounded-3xl shadow-[0_25px_80px_rgba(0,0,0,0.9)] p-8 max-h-[85vh] overflow-y-auto">
            
            <button
              onClick={() => setSavedModalOpen(false)}
              className="absolute top-4 right-4 p-2.5 rounded-full bg-[#121212] border border-[#2D2A26] text-[#EAE6DF] hover:text-[#C5A059] transition-all"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center justify-between mb-6 pb-4 border-b border-[#2D2A26]">
              <div>
                <h3 className="font-serif text-2xl font-bold text-[#EAE6DF]">Saved Residences</h3>
                <p className="text-xs text-[#A39E93]">Your shortlisted luxury properties ({savedIds.length})</p>
              </div>
              {savedIds.length > 0 && (
                <button
                  onClick={() => setSavedIds([])}
                  className="flex items-center gap-1.5 text-xs text-red-400 hover:text-red-300 transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Clear All</span>
                </button>
              )}
            </div>

            {savedProperties.length > 0 ? (
              <div className="flex flex-col gap-4">
                {savedProperties.map((prop) => (
                  <div key={prop.id} className="flex flex-col sm:flex-row items-center justify-between gap-4 p-4 rounded-2xl bg-[#121212] border border-[#2D2A26] hover:border-[#C5A059]/50 transition-colors">
                    <div className="flex items-center gap-4 w-full sm:w-auto">
                      <img src={prop.image} alt={prop.title} className="w-24 h-20 rounded-xl object-cover" />
                      <div>
                        <span className="text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold">{prop.location}</span>
                        <h4 className="font-serif text-lg font-bold text-[#EAE6DF]">{prop.title}</h4>
                        <span className="text-sm font-semibold gold-gradient-text">{prop.formattedPrice}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
                      <button
                        onClick={() => {
                          setSavedModalOpen(false);
                          setSelectedProperty(prop);
                        }}
                        className="px-4 py-2 rounded-xl bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs uppercase tracking-widest"
                      >
                        View
                      </button>
                      <button
                        onClick={() => handleToggleSave(prop.id)}
                        className="p-2.5 rounded-xl border border-[#2D2A26] text-[#A39E93] hover:text-red-400"
                        title="Remove"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-16">
                <p className="font-serif text-lg text-[#EAE6DF] mb-2">No saved properties yet</p>
                <p className="text-xs text-[#A39E93]">Click the heart icon on any property card to save residences to your shortlist.</p>
              </div>
            )}

          </div>
        </div>
      )}

    </div>
  );
}
