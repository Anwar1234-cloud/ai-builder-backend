import React, { useState, useEffect } from 'react';
import { Compass, Menu, X, Phone, User, Heart } from 'lucide-react';

export const Navbar = ({ onOpenInquiry, onOpenSaved, savedCount }) => {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      if (window.scrollY > 40) {
        setScrolled(true);
      } else {
        setScrolled(false);
      }
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <header className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
      scrolled 
        ? 'bg-[#121212]/90 backdrop-blur-md border-b border-[#2D2A26] py-4 shadow-2xl' 
        : 'bg-gradient-to-b from-[#121212]/80 to-transparent py-6'
    }`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between">
          
          {/* Brand Logo */}
          <a href="#" className="flex items-center gap-3 group">
            <div className="w-10 h-10 rounded-full border border-[#C5A059] flex items-center justify-center bg-[#181715] group-hover:bg-[#C5A059]/10 transition-colors">
              <Compass className="w-5 h-5 text-[#C5A059]" />
            </div>
            <div className="flex flex-col">
              <span className="font-serif text-2xl tracking-[0.2em] font-bold text-[#EAE6DF] group-hover:text-[#C5A059] transition-colors">
                AURELIA
              </span>
              <span className="text-[9px] tracking-[0.4em] uppercase text-[#C5A059] font-medium -mt-1">
                ESTATES
              </span>
            </div>
          </a>

          {/* Desktop Navigation Links */}
          <nav className="hidden md:flex items-center gap-8">
            <a href="#properties" className="text-sm tracking-wider uppercase text-[#EAE6DF]/80 hover:text-[#C5A059] transition-colors">
              Properties
            </a>
            <a href="#about" className="text-sm tracking-wider uppercase text-[#EAE6DF]/80 hover:text-[#C5A059] transition-colors">
              The Brand
            </a>
            <a href="#services" className="text-sm tracking-wider uppercase text-[#EAE6DF]/80 hover:text-[#C5A059] transition-colors">
              Services
            </a>
            <a href="#testimonials" className="text-sm tracking-wider uppercase text-[#EAE6DF]/80 hover:text-[#C5A059] transition-colors">
              Testimonials
            </a>
            <a href="#contact" className="text-sm tracking-wider uppercase text-[#EAE6DF]/80 hover:text-[#C5A059] transition-colors">
              Contact
            </a>
          </nav>

          {/* Action CTAs */}
          <div className="hidden md:flex items-center gap-4">
            <button 
              onClick={onOpenSaved}
              className="relative p-2.5 rounded-full border border-[#2D2A26] bg-[#181715] hover:border-[#C5A059] text-[#EAE6DF] hover:text-[#C5A059] transition-all"
              title="Saved Properties"
            >
              <Heart className="w-4 h-4" />
              {savedCount > 0 && (
                <span className="absolute -top-1 -right-1 w-5 h-5 bg-[#C5A059] text-[#121212] font-bold text-[10px] rounded-full flex items-center justify-center">
                  {savedCount}
                </span>
              )}
            </button>

            <a 
              href="tel:+18005550199" 
              className="flex items-center gap-2 text-xs tracking-wider text-[#A39E93] hover:text-[#EAE6DF] px-3 py-2 transition-colors"
            >
              <Phone className="w-3.5 h-3.5 text-[#C5A059]" />
              <span>+1 (800) 555-0199</span>
            </a>

            <button
              onClick={onOpenInquiry}
              className="relative group overflow-hidden rounded-full px-6 py-2.5 bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-widest uppercase transition-all duration-300 hover:shadow-[0_0_25px_rgba(197,160,89,0.4)] hover:scale-[1.02] active:scale-[0.98]"
            >
              <span className="relative z-10">Explore Properties</span>
              <div className="absolute inset-0 bg-white/20 opacity-0 group-hover:opacity-100 transition-opacity"></div>
            </button>
          </div>

          {/* Mobile Menu Button */}
          <div className="flex md:hidden items-center gap-3">
            <button 
              onClick={onOpenSaved}
              className="relative p-2 rounded-full border border-[#2D2A26] bg-[#181715] text-[#EAE6DF]"
            >
              <Heart className="w-4 h-4" />
              {savedCount > 0 && (
                <span className="absolute -top-1 -right-1 w-4 h-4 bg-[#C5A059] text-[#121212] font-bold text-[9px] rounded-full flex items-center justify-center">
                  {savedCount}
                </span>
              )}
            </button>
            
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2.5 rounded-lg border border-[#2D2A26] bg-[#181715] text-[#EAE6DF]"
              aria-label="Toggle Menu"
            >
              {mobileMenuOpen ? <X className="w-5 h-5 text-[#C5A059]" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>

        </div>
      </div>

      {/* Mobile Dropdown Menu */}
      {mobileMenuOpen && (
        <div className="md:hidden absolute top-full left-0 right-0 bg-[#181715] border-b border-[#2D2A26] p-6 shadow-2xl animate-fadeIn">
          <nav className="flex flex-col gap-4 mb-6">
            <a 
              href="#properties" 
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm tracking-wider uppercase text-[#EAE6DF] hover:text-[#C5A059] py-2 border-b border-[#2D2A26]/50"
            >
              Properties
            </a>
            <a 
              href="#about" 
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm tracking-wider uppercase text-[#EAE6DF] hover:text-[#C5A059] py-2 border-b border-[#2D2A26]/50"
            >
              The Brand
            </a>
            <a 
              href="#services" 
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm tracking-wider uppercase text-[#EAE6DF] hover:text-[#C5A059] py-2 border-b border-[#2D2A26]/50"
            >
              Services
            </a>
            <a 
              href="#testimonials" 
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm tracking-wider uppercase text-[#EAE6DF] hover:text-[#C5A059] py-2 border-b border-[#2D2A26]/50"
            >
              Testimonials
            </a>
            <a 
              href="#contact" 
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm tracking-wider uppercase text-[#EAE6DF] hover:text-[#C5A059] py-2"
            >
              Contact
            </a>
          </nav>
          
          <div className="flex flex-col gap-3">
            <button
              onClick={() => { setMobileMenuOpen(false); onOpenInquiry(); }}
              className="w-full py-3 rounded-lg bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-widest uppercase text-center shadow-lg"
            >
              Explore Properties
            </button>
            <a
              href="tel:+18005550199"
              className="flex items-center justify-center gap-2 py-3 rounded-lg border border-[#2D2A26] text-xs text-[#EAE6DF]"
            >
              <Phone className="w-3.5 h-3.5 text-[#C5A059]" />
              <span>Call Concierge: +1 (800) 555-0199</span>
            </a>
          </div>
        </div>
      )}
    </header>
  );
};
