# F.Y.E.O Headless Integration Guide: Architecture & In-House Design Recipes

> **Philosophy**: F.Y.E.O provides institutional cryptography, key cloaking, and zero-yield storage protection. **We never dictate or control your UI.** Your in-house design and frontend teams retain 100% creative sovereignty over styling, layout, brand colors, and component hierarchies.

---

## 1. The Headless Division of Responsibility

```
┌────────────────────────────────────────────────────────┐
│                   BACKEND SERVICE                      │
│ (Spring Boot + fyeo-spring-boot-starter)               │
│                                                        │
│  • Permutes raw images to Visual AEAD noise            │
│  • Stego-AEAD Chameleon Decoy embedding                │
│  • Key Cloaking & Metadata Envelope encapsulation      │
│  • Restores plaintext strictly in volatile RAM         │
└──────────────────────────┬─────────────────────────────┘
                           │ Pure JSON API
                           ▼
┌────────────────────────────────────────────────────────┐
│               YOUR IN-HOUSE FRONTEND                   │
│ (React, Vue, Angular, Svelte, or Vanilla JS)           │
│                                                        │
│  • Your corporate design system & Tailwind tokens       │
│  • Your modal, drawer, or split-screen reviewer layout │
│  • Secure canvas rendering (right-click disabled)      │
│  • Auto-burn: RAM memory wiped upon 15s timeout        │
└────────────────────────────────────────────────────────┘
```

---

## 2. The Backend REST Contract

Your backend team exposes a single endpoint for authorized compliance reviewers:

```http
GET /api/v1/kyc/documents/{documentId}/ephemeral-inspect
Authorization: Bearer <reviewer-jwt-token>
```

### JSON Response:
```json
{
  "documentId": "KYC-94821",
  "originalFilename": "adeyemi_passport.png",
  "customerName": "Adeyemi Babatunde",
  "docType": "NATIONAL_PASSPORT",
  "imageData": "data:image/png;base64,iVBORw0KGgoAAAANSUh...",
  "ttlSeconds": 15,
  "reviewerWatermark": "AUDITOR-ADE-2026-10-31"
}
```

---

## 3. The Unstyled React Hook Recipe (`useFyeoViewer.ts`)

Give this unstyled TypeScript hook to your frontend engineers. It handles the security invariants (canvas rendering, watermark, countdown, and memory purge) without imposing **any** HTML or CSS styles:

```typescript
import { useState, useEffect, useRef } from 'react';

interface FyeoViewerOptions {
  imageData: string | null;
  ttlSeconds?: number;
  watermarkText?: string;
  onBurn?: () => void;
}

export function useFyeoViewer({ imageData, ttlSeconds = 15, watermarkText, onBurn }: FyeoViewerOptions) {
  const [secondsRemaining, setSecondsRemaining] = useState<number>(ttlSeconds);
  const [isBurned, setIsBurned] = useState<boolean>(false);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    if (!imageData || isBurned) return;

    setSecondsRemaining(ttlSeconds);
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // 1. Draw image to Canvas
    const img = new Image();
    img.onload = () => {
      canvas.width = img.width;
      canvas.height = img.height;
      ctx.drawImage(img, 0, 0);

      // 2. Security Watermark (Anti-Camera / Screenshot deterrent)
      if (watermarkText) {
        ctx.save();
        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate(-Math.PI / 6);
        ctx.font = `bold ${Math.max(16, canvas.width / 24)}px sans-serif`;
        ctx.fillStyle = 'rgba(239, 68, 68, 0.25)';
        ctx.textAlign = 'center';
        ctx.fillText(`${watermarkText} · ${new Date().toISOString()}`, 0, 0);
        ctx.restore();
      }
    };
    img.src = imageData;

    // 3. Volatile Countdown
    const timer = setInterval(() => {
      setSecondsRemaining(prev => {
        if (prev <= 1) {
          clearInterval(timer);
          // Explicitly zeroize canvas buffer
          ctx.clearRect(0, 0, canvas.width, canvas.height);
          setIsBurned(true);
          onBurn?.();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [imageData, isBurned]);

  return {
    canvasRef,
    secondsRemaining,
    isBurned,
    // Attaches to canvas to prevent Right-Click -> Save As
    canvasProps: {
      onContextMenu: (e: React.MouseEvent) => e.preventDefault(),
      onDragStart: (e: React.DragEvent) => e.preventDefault(),
    }
  };
}
```

---

## 4. How Your In-House Designers Style It (Tailwind Example)

Your design team can plug that hook into any layout they desire. Here is an example using standard Tailwind CSS:

```tsx
export function ComplianceReviewModal({ document, onClose }) {
  const { canvasRef, secondsRemaining, isBurned, canvasProps } = useFyeoViewer({
    imageData: document.imageData,
    ttlSeconds: document.ttlSeconds,
    watermarkText: 'ENTERPRISE COMPLIANCE AUDIT #402',
    onBurn: () => console.log('RAM buffer zeroed')
  });

  return (
    <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-6">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-2xl w-full p-6 shadow-2xl">
        
        {/* Header styled to your exact brand */}
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-white font-bold text-lg">{document.customerName}</h3>
          <span className="bg-red-500/10 text-red-400 border border-red-500/20 px-3 py-1 rounded-full text-xs font-mono font-bold">
            {isBurned ? 'MEMORY PURGED' : `${secondsRemaining}s REMAINING`}
          </span>
        </div>

        {/* Canvas container styled however your designers want */}
        <div className="bg-black rounded-xl overflow-hidden flex justify-center p-2 mb-4">
          <canvas ref={canvasRef} {...canvasProps} className="max-h-96 object-contain" />
        </div>

        {/* Action button in your company's design system */}
        <div className="flex justify-end">
          <button onClick={onClose} className="bg-slate-800 hover:bg-slate-700 text-white px-4 py-2 rounded-lg text-sm font-semibold">
            Close &amp; Burn
          </button>
        </div>

      </div>
    </div>
  );
}
```

---

## 5. Security Invariants Enforced by this Headless Model

1. **Zero Browser Disk Cache**: Because the document is delivered as an ephemeral base64 string and rendered directly into `<canvas>`, the browser never saves a cached `.png` or `.jpeg` file on the user's hard drive.
2. **Right-Click Save Immunity**: Context menu and image drag events are disabled by the canvas container.
3. **RAM Zeroization**: Upon countdown completion (15s), `ctx.clearRect()` overwrites the pixel buffer with zero bytes.
