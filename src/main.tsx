import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import ErrorBoundary from './components/ErrorBoundary';
import './index.css';

// Global error handler for catching issues before React takes over
window.onerror = function(message, source, lineno, colno, error) {
  console.error("Global Error:", message, "at", source, ":", lineno, ":", colno, error);
  // If we have a global error before React, we might want to manually show something
  return false;
};

// Catch unhandled promise rejections
window.onunhandledrejection = function(event) {
  console.error("Unhandled Rejection:", event.reason);
};

const rootElement = document.getElementById('root');

const initApp = () => {
  if (!rootElement) {
    console.error("Failed to find root element");
    return;
  }
  
  try {
    const root = createRoot(rootElement);
    root.render(
      <ErrorBoundary>
        <StrictMode>
          <App />
        </StrictMode>
      </ErrorBoundary>,
    );
    
    // If we've got this far without a crash, hide any potential boot error messages
    const bootError = document.getElementById('boot-error');
    if (bootError) bootError.style.display = 'none';
  } catch (err) {
    console.error("Critical error during app initialization:", err);
    const bootError = document.getElementById('boot-error');
    if (bootError) {
      bootError.style.display = 'block';
      bootError.style.backgroundColor = '#374151'; // Ensure background for visibility if CSS failed
      bootError.style.position = 'fixed';
      bootError.style.inset = '0';
      bootError.style.zIndex = '9999';
    }
  }
};

// Ensure initialization happens accurately
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
} else {
    initApp();
}
