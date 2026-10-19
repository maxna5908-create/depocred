import React from 'react';

interface Props {
  children?: React.ReactNode;
}

interface State {
  hasError: boolean;
}

class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(_: Error): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("Uncaught error:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="h-screen bg-slate-900 flex items-center justify-center p-6 text-center">
          <div className="bg-white/10 backdrop-blur-xl p-8 rounded-3xl border border-white/20">
            <h1 className="text-white text-2xl font-bold mb-4">Упс! Что-то пошло не так.</h1>
            <p className="text-slate-300 mb-6">Пожалуйста, перезапустите приложение.</p>
            <button 
              onClick={() => window.location.reload()}
              className="bg-emerald-500 text-white px-6 py-2 rounded-xl font-bold"
            >
              Перезагрузить
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
