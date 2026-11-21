import { useEffect, useRef, useState } from 'react';
import { Asset, Icon } from './components';

export default function CameraCapture({ busy, onCapture, onGallery }: { busy: boolean; onCapture: (file: File) => Promise<void>; onGallery: () => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const capturing = useRef(false);
  const [ready, setReady] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    let stream: MediaStream | undefined;
    async function start() {
      if (!navigator.mediaDevices?.getUserMedia) { setError('카메라를 사용할 수 없어요. HTTPS 또는 localhost에서 접속하거나 앨범에서 이미지를 선택해 주세요.'); return; }
      try {
        const media = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false });
        if (!active) { media.getTracks().forEach(track => track.stop()); return; }
        stream = media;
        if (video.current) { video.current.srcObject = media; await video.current.play(); }
      } catch (e) {
        if (!active) return;
        stream?.getTracks().forEach(track => track.stop());
        setError(e instanceof DOMException && e.name === 'NotAllowedError' ? '카메라 권한을 허용한 뒤 촬영 화면을 다시 열어주세요. 앨범에서 선택할 수도 있어요.' : '카메라를 연결하지 못했어요. 기기를 확인하거나 앨범에서 선택해 주세요.');
      }
    }
    void start();
    return () => { active = false; stream?.getTracks().forEach(track => track.stop()); };
  }, []);
  async function capture() {
    const frame = video.current;
    if (!frame || !ready || !frame.videoWidth || !frame.videoHeight || busy || capturing.current) return;
    capturing.current = true; setPending(true); setError('');
    try {
      const canvas = document.createElement('canvas');
      const scale = Math.min(1, 2560 / Math.max(frame.videoWidth, frame.videoHeight));
      canvas.width = Math.round(frame.videoWidth * scale); canvas.height = Math.round(frame.videoHeight * scale);
      const context = canvas.getContext('2d');
      if (!context) throw new Error('촬영 이미지를 만들지 못했어요. 다시 촬영해 주세요.');
      context.drawImage(frame, 0, 0, canvas.width, canvas.height);
      const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('촬영 이미지를 만들지 못했어요. 다시 촬영해 주세요.')), 'image/jpeg', 0.92));
      await onCapture(new File([blob], 'camera.jpg', { type: 'image/jpeg' }));
    } catch (e) { setError(e instanceof Error ? e.message : '촬영하지 못했어요. 다시 시도해 주세요.'); }
    finally { capturing.current = false; setPending(false); }
  }
  return <div className="camera-screen">
    <p className="recognition-banner"><Icon name="shield" size={16}/>안내문의 일시와 준비물이 잘 보이도록 맞춰주세요</p>
    <div className="scan-frame"><i/><i/><i/><i/><video ref={video} autoPlay muted playsInline aria-label="실시간 카메라 화면" onLoadedData={() => setReady(true)}/></div>
    <p className="camera-hint" role="status">{error || (!ready ? '카메라를 연결하고 있어요. 접근 권한을 허용해 주세요.' : '촬영 버튼을 누르면 바로 안내문 분석을 시작해요.')}</p>
    <div className="camera-bottom"><div className="camera-controls">
      <button className="camera-gallery" type="button" aria-label="앨범에서 선택" disabled={busy || pending} onClick={onGallery}><Asset id="23c16"/></button>
      <button className="shutter" type="button" aria-label="안내문 촬영" disabled={!ready || busy || pending} onClick={() => void capture()}><span/></button>
      <button className="camera-flash" type="button" disabled aria-label="플래시 미지원">ϟ</button>
    </div><p>메일·문자 캡처는 앨범에서 선택하면 바로 분석해요.</p></div>
  </div>;
}
