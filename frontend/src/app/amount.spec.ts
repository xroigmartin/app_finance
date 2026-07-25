import { parseAmount } from './amount';

describe('parseAmount', () => {
  it('pasa un número tal cual', () => {
    expect(parseAmount(1234.56)).toBe(1234.56);
  });

  it('acepta coma decimal', () => {
    expect(parseAmount('1234,56')).toBe(1234.56);
  });

  it('acepta punto decimal', () => {
    expect(parseAmount('1234.56')).toBe(1234.56);
  });

  it('recorta espacios', () => {
    expect(parseAmount('  42  ')).toBe(42);
  });

  it('devuelve NaN para una cadena vacía', () => {
    expect(parseAmount('')).toBeNaN();
  });

  it('devuelve NaN para una cadena vacía tras recortar espacios', () => {
    expect(parseAmount('   ')).toBeNaN();
  });

  it('devuelve NaN para texto no numérico', () => {
    expect(parseAmount('abc')).toBeNaN();
  });
});
