// Simple script to create PNG icons from an SVG
// Run with: node scripts/generate-icons.js
// For now we use inline SVG data URIs in the HTML as fallback

const fs = require("fs");
const path = require("path");

// Create a simple SVG icon
const createSvg = (size) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <rect width="${size}" height="${size}" rx="${size * 0.2}" fill="#4f46e5"/>
  <text x="50%" y="55%" text-anchor="middle" dominant-baseline="middle" font-family="Arial, sans-serif" font-weight="bold" font-size="${size * 0.28}" fill="white">My</text>
  <text x="50%" y="78%" text-anchor="middle" dominant-baseline="middle" font-family="Arial, sans-serif" font-weight="bold" font-size="${size * 0.2}" fill="#818cf8">Meds</text>
  <rect x="${size * 0.35}" y="${size * 0.08}" width="${size * 0.3}" height="${size * 0.15}" rx="${size * 0.03}" fill="white" opacity="0.9"/>
  <line x1="${size * 0.5}" y1="${size * 0.1}" x2="${size * 0.5}" y2="${size * 0.21}" stroke="#4f46e5" stroke-width="${size * 0.03}" stroke-linecap="round"/>
  <line x1="${size * 0.42}" y1="${size * 0.155}" x2="${size * 0.58}" y2="${size * 0.155}" stroke="#4f46e5" stroke-width="${size * 0.03}" stroke-linecap="round"/>
</svg>`;

// Write SVG files (browsers can use these)
fs.writeFileSync(
  path.join(__dirname, "../public/icon-192.svg"),
  createSvg(192)
);
fs.writeFileSync(
  path.join(__dirname, "../public/icon-512.svg"),
  createSvg(512)
);

console.log("SVG icons generated!");
console.log("Note: For production PNG icons, convert these SVGs to PNG using an image tool.");
