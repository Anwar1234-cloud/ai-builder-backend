import React, { useState } from 'react';
import { Compass, Phone, Mail, MapPin, Globe, CheckCircle2 } from 'lucide-react';

export const Footer = () => {
  const [newsletterEmail, setNewsletterEmail] = useState('');
  const [subscribed, setSubscribed] = useState(false);

  const handleSubscribe = (e) => {
    e.preventDefault();
    if (newsletterEmail) {
      setSubscribed(true);
      setNewsletterEmail('');
    }
  };

  return (
    <footer id="contact" className="bg-[#0A0A0A] border-t border-[#2D2A26] pt-20 pb-12 text-[#A39E93]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Top Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-12 pb-16 border-b border-[#2D2A26]">
          
          {/* Brand Info */}
          <div className="lg:col-span-4">
            <a href="#" className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 rounded-full border border-[#C5A059] flex items-center justify-center bg-[#181715]">
                <Compass className="w-5 h-5 text-[#C5A059]" />
              </div>
              <div className="flex flex-col">
                <span className="font-serif text-2xl tracking-[0.2em] font-bold text-[#EAE6DF]">
                  AURELIA
                </span>
                <span className="text-[9px] tracking-[0.4em] uppercase text-[#C5A059] font-medium -mt-1">
                  ESTATES
                </span>
              </div>
            </a>
            
            <p className="text-xs font-light leading-relaxed mb-6">
              The premier global luxury real estate brand representing extraordinary architectural residences, penthouses, and private estates worldwide.
            </p>

            <div className="flex items-center gap-4">
              <a href="#instagram" className="w-9 h-9 rounded-full border border-[#2D2A26] bg-[#181715] flex items-center justify-center text-[#EAE6DF] hover:text-[#C5A059] hover:border-[#C5A059] transition-colors" aria-label="Globe">
                <Globe className="w-4 h-4" />
              </a>
              <a href="#phone" className="w-9 h-9 rounded-full border border-[#2D2A26] bg-[#181715] flex items-center justify-center text-[#EAE6DF] hover:text-[#C5A059] hover:border-[#C5A059] transition-colors" aria-label="Phone">
                <Phone className="w-4 h-4" />
              </a>
              <a href="#mail" className="w-9 h-9 rounded-full border border-[#2D2A26] bg-[#181715] flex items-center justify-center text-[#EAE6DF] hover:text-[#C5A059] hover:border-[#C5A059] transition-colors" aria-label="Mail">
                <Mail className="w-4 h-4" />
              </a>
              <a href="#map" className="w-9 h-9 rounded-full border border-[#2D2A26] bg-[#181715] flex items-center justify-center text-[#EAE6DF] hover:text-[#C5A059] hover:border-[#C5A059] transition-colors" aria-label="MapPin">
                <MapPin className="w-4 h-4" />
              </a>
            </div>
          </div>

          {/* Quick Links */}
          <div className="lg:col-span-2">
            <h4 className="font-serif text-sm font-bold tracking-widest uppercase text-[#EAE6DF] mb-6">
              Navigation
            </h4>
            <ul className="flex flex-col gap-3 text-xs">
              <li><a href="#properties" className="hover:text-[#C5A059] transition-colors">Properties</a></li>
              <li><a href="#about" className="hover:text-[#C5A059] transition-colors">The Brand</a></li>
              <li><a href="#services" className="hover:text-[#C5A059] transition-colors">Services</a></li>
              <li><a href="#testimonials" className="hover:text-[#C5A059] transition-colors">Testimonials</a></li>
              <li><a href="#contact" className="hover:text-[#C5A059] transition-colors">Contact</a></li>
            </ul>
          </div>

          {/* Global Hubs */}
          <div className="lg:col-span-3">
            <h4 className="font-serif text-sm font-bold tracking-widest uppercase text-[#EAE6DF] mb-6">
              Global Hubs
            </h4>
            <ul className="flex flex-col gap-3 text-xs font-light">
              <li className="flex items-center gap-2"><MapPin className="w-3.5 h-3.5 text-[#C5A059]" /> Miami &bull; Biscayne Bay</li>
              <li className="flex items-center gap-2"><MapPin className="w-3.5 h-3.5 text-[#C5A059]" /> Los Angeles &bull; Bel Air</li>
              <li className="flex items-center gap-2"><MapPin className="w-3.5 h-3.5 text-[#C5A059]" /> New York &bull; Manhattan</li>
              <li className="flex items-center gap-2"><MapPin className="w-3.5 h-3.5 text-[#C5A059]" /> Dubai &bull; Palm Jumeirah</li>
              <li className="flex items-center gap-2"><MapPin className="w-3.5 h-3.5 text-[#C5A059]" /> French Riviera &bull; Cap Ferrat</li>
            </ul>
          </div>

          {/* Newsletter / Private Journal */}
          <div className="lg:col-span-3">
            <h4 className="font-serif text-sm font-bold tracking-widest uppercase text-[#EAE6DF] mb-6">
              Private Journal
            </h4>
            <p className="text-xs font-light mb-4">
              Receive confidential invitations to off-market ultra-prime property listings.
            </p>

            {subscribed ? (
              <div className="flex items-center gap-2 p-3 rounded-xl bg-[#C5A059]/10 border border-[#C5A059]/40 text-[#C5A059] text-xs">
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>Subscribed to Aurelia Private Journal.</span>
              </div>
            ) : (
              <form onSubmit={handleSubscribe} className="flex flex-col gap-2">
                <input
                  type="email"
                  required
                  placeholder="Enter your email address"
                  value={newsletterEmail}
                  onChange={(e) => setNewsletterEmail(e.target.value)}
                  className="w-full bg-[#181715] border border-[#2D2A26] rounded-xl px-4 py-2.5 text-xs text-[#EAE6DF] placeholder-[#A39E93] focus:outline-none focus:border-[#C5A059]"
                />
                <button
                  type="submit"
                  className="w-full py-2.5 rounded-xl bg-gradient-to-r from-[#C5A059] to-[#A6823F] text-[#121212] font-semibold text-xs tracking-widest uppercase transition-all"
                >
                  Subscribe
                </button>
              </form>
            )}
          </div>

        </div>

        {/* Bottom Legal */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between text-xs font-light gap-4">
          <p>&copy; {new Date().getFullYear()} Aurelia Estates Inc. All Rights Reserved.</p>
          <div className="flex items-center gap-6">
            <a href="#" className="hover:text-[#C5A059] transition-colors">Privacy Policy</a>
            <a href="#" className="hover:text-[#C5A059] transition-colors">Terms of Service</a>
            <a href="#" className="hover:text-[#C5A059] transition-colors">Equal Housing Opportunity</a>
          </div>
        </div>

      </div>
    </footer>
  );
};
