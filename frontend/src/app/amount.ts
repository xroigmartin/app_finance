/**
 * Convierte el texto de un campo de importe a número, aceptando coma o punto
 * como separador decimal ("1234,56" y "1234.56"). Devuelve NaN si no es válido.
 */
export function parseAmount(value: string | number): number {
  if (typeof value === 'number') return value;
  const text = value.trim().replace(',', '.');
  return text === '' ? NaN : Number(text);
}
