const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const src = path.resolve(root, '..', 'frontend', 'm');
const dest = path.resolve(root, 'www');
const frontendRoot = path.resolve(root, '..', 'frontend');

function copiarDir(origem, destino) {
  if (!fs.existsSync(origem)) {
    throw new Error('Diretório origem não encontrado: ' + origem);
  }
  if (!fs.existsSync(destino) {
    fs.mkdirSync(destino, { recursive: true });
  }
  const entradas = fs.readdirSync(origem, { withFileTypes: true });
  for (const entrada of entradas) {
    const origPath = path.join(origem, entrada.name);
    const destPath = path.join(destino, entrada.name);
    if (entrada.isDirectory()) {
      copiarDir(origPath, destPath);
    } else {
      fs.copyFileSync(origPath, destPath);
    }
  }
}

function copiarArquivo(origem, destino) {
  if (!fs.existsSync(origem)) {
    return;
  }
  const dir = path.dirname(destino);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  fs.copyFileSync(origem, destino);
}

console.log('Copiando app móvel para driver-app/www...');
if (fs.existsSync(dest)) {
  fs.rmSync(dest, { recursive: true, force: true });
}
fs.mkdirSync(dest, { recursive: true });
copiarDir(src, dest);

copiarArquivo(path.join(frontendRoot, 'js', 'config.js'), path.join(dest, 'js', 'config.js'));
copiarArquivo(path.join(frontendRoot, 'img', 'favicon.svg'), path.join(dest, 'img', 'favicon.svg'));
copiarArquivo(path.join(frontendRoot, 'img', 'icon-192.png'), path.join(dest, 'img', 'icon-192.png'));
copiarArquivo(path.join(frontendRoot, 'img', 'icon-512.png'), path.join(dest, 'img', 'icon-512.png'));

const indexPath = path.join(dest, 'index.html');
let html = fs.readFileSync(indexPath, 'utf8');
html = html.replace(/<link rel="manifest" href="manifest\.webmanifest">/i,
  '<!-- PWA desativado na app nativa -->');
html = html.replace(/<script src="js\/app\.js"><\/script>/i,
  '<script src="js/app.js"></script>\n    <script src="js/capacitor-bridge.js"></script>');
fs.writeFileSync(indexPath, html);

fs.writeFileSync(path.join(dest, 'capacitor-bridge.js'), `/* Bridge Capacitor para Logitrack Motorista */
(function(){
  'use strict';
  if (!window.Capacitor) return;
  const { Camera, CameraResultType, CameraSource } = window.Capacitor.Plugins;
  const { Geolocation } = window.Capacitor.Plugins;
  const { StatusBar } = window.Capacitor.Plugins;
  const { SplashScreen } = window.Capacitor.Plugins;
  const { App } = window.Capacitor.Plugins;
  const { Network } = window.Capacitor.Plugins;

  if (StatusBar && StatusBar.setStyle) {
    try { StatusBar.setStyle({ style: 'LIGHT' }); } catch(e){}
  }
  if (StatusBar && StatusBar.setBackgroundColor) {
    try { StatusBar.setBackgroundColor({ color: '#0b1f3a' }); } catch(e){}
  }
  if (SplashScreen && SplashScreen.hide) {
    setTimeout(function(){ SplashScreen.hide(); }, 800);
  }
  if (App && App.addListener) {
    App.addListener('backButton', function({ canGoBack }){
      if (canGoBack) {
        window.history.back();
      } else {
        App.exitApp();
      }
    });
  }
  if (Network && Network.addListener) {
    Network.getStatus().then(function(s){
      window.dispatchEvent(new Event(s.connected ? 'online' : 'offline'));
    });
    Network.addListener('networkStatusChange', function(s){
      window.dispatchEvent(new Event(s.connected ? 'online' : 'offline'));
    });
  }
  window.LT_NATIVE = {
    camera: Camera,
    geolocation: Geolocation,
    network: Network,
    app: App
  };
})();
`);

console.log('Cópia concluída em driver-app/www');
console.log('Próximos passos:');
console.log('1. cd driver-app');
console.log('2. npm install');
console.log('3. npx cap add android');
console.log('4. npm run android  (abre Android Studio) ou npm run build:apk');
