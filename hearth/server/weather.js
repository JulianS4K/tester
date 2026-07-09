import { config } from './config.js';

/**
 * Open-Meteo current + daily forecast. Free, no API key. Returns a small,
 * UI-ready shape or null on failure.
 */

// WMO weather-code → emoji + label (compact subset).
const WMO = {
  0: ['☀️', 'Clear'], 1: ['🌤️', 'Mainly clear'], 2: ['⛅', 'Partly cloudy'], 3: ['☁️', 'Overcast'],
  45: ['🌫️', 'Fog'], 48: ['🌫️', 'Fog'],
  51: ['🌦️', 'Light drizzle'], 53: ['🌦️', 'Drizzle'], 55: ['🌧️', 'Drizzle'],
  61: ['🌧️', 'Light rain'], 63: ['🌧️', 'Rain'], 65: ['🌧️', 'Heavy rain'],
  66: ['🌧️', 'Freezing rain'], 67: ['🌧️', 'Freezing rain'],
  71: ['🌨️', 'Light snow'], 73: ['🌨️', 'Snow'], 75: ['❄️', 'Heavy snow'], 77: ['🌨️', 'Snow grains'],
  80: ['🌦️', 'Showers'], 81: ['🌧️', 'Showers'], 82: ['⛈️', 'Violent showers'],
  85: ['🌨️', 'Snow showers'], 86: ['❄️', 'Snow showers'],
  95: ['⛈️', 'Thunderstorm'], 96: ['⛈️', 'Thunderstorm'], 99: ['⛈️', 'Thunderstorm'],
};

function describe(code) {
  return WMO[code] || ['🌡️', ''];
}

export async function getWeather() {
  const { lat, lon, unit } = config.weather;
  const url = new URL('https://api.open-meteo.com/v1/forecast');
  url.searchParams.set('latitude', lat);
  url.searchParams.set('longitude', lon);
  url.searchParams.set('current', 'temperature_2m,weather_code');
  url.searchParams.set('daily', 'weather_code,temperature_2m_max,temperature_2m_min');
  url.searchParams.set('temperature_unit', unit === 'celsius' ? 'celsius' : 'fahrenheit');
  url.searchParams.set('timezone', 'auto');
  url.searchParams.set('forecast_days', '4');

  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    const d = await res.json();
    const [icon, label] = describe(d.current?.weather_code ?? 0);
    const daily = (d.daily?.time || []).map((date, i) => {
      const [dIcon] = describe(d.daily.weather_code[i]);
      return {
        date,
        icon: dIcon,
        max: Math.round(d.daily.temperature_2m_max[i]),
        min: Math.round(d.daily.temperature_2m_min[i]),
      };
    });
    return {
      unit: unit === 'celsius' ? 'C' : 'F',
      current: { temp: Math.round(d.current?.temperature_2m ?? 0), icon, label },
      daily,
    };
  } catch (e) {
    console.warn('[weather] failed:', e.message);
    return null;
  }
}
