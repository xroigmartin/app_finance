import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../api.service';
import { Account, ImportResult } from '../models';
import { ImportDialog } from './import-dialog';

describe('ImportDialog', () => {
  let api: {
    getAccounts: ReturnType<typeof vi.fn>;
    importTransactions: ReturnType<typeof vi.fn>;
    importTransfers: ReturnType<typeof vi.fn>;
  };

  const account: Account = { id: 1, name: 'Corriente', type: 'Banco', initialBalance: 0 };
  const okResult: ImportResult = { imported: 5, duplicated: 1, errors: [] };

  function create(kind: 'transactions' | 'transfers' = 'transactions'): ImportDialog {
    api = {
      getAccounts: vi.fn().mockReturnValue(of([account])),
      importTransactions: vi.fn().mockReturnValue(of(okResult)),
      importTransfers: vi.fn().mockReturnValue(of(okResult)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const dialog = TestBed.createComponent(ImportDialog).componentInstance;
    dialog.kind = kind;
    dialog.ngOnInit();
    return dialog;
  }

  function fakeInput(file: File | null): HTMLInputElement {
    return { files: file ? [file] : [], value: 'x' } as unknown as HTMLInputElement;
  }

  it('ngOnInit carga cuentas y preselecciona la primera solo para movimientos', () => {
    const dialog = create('transactions');
    expect(dialog.accounts).toEqual([account]);
    expect(dialog.accountId).toBe(1);
  });

  it('ngOnInit no toca cuentas para transferencias', () => {
    api = {
      getAccounts: vi.fn().mockReturnValue(of([account])),
      importTransactions: vi.fn(),
      importTransfers: vi.fn(),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    const dialog = TestBed.createComponent(ImportDialog).componentInstance;
    dialog.kind = 'transfers';
    dialog.ngOnInit();
    expect(api.getAccounts).not.toHaveBeenCalled();
  });

  it('open resetea el estado del diálogo', () => {
    const dialog = create();
    dialog.file = new File(['x'], 'x.csv');
    dialog.result = okResult;
    dialog.error = 'algo';
    dialog.open();
    expect(dialog.visible).toBe(true);
    expect(dialog.file).toBeNull();
    expect(dialog.result).toBeNull();
    expect(dialog.error).toBe('');
  });

  it('close oculta el diálogo', () => {
    const dialog = create();
    dialog.visible = true;
    dialog.close();
    expect(dialog.visible).toBe(false);
  });

  it('onFile toma el fichero, limpia el input y resetea resultado/error', () => {
    const dialog = create();
    dialog.result = okResult;
    dialog.error = 'algo';
    const file = new File(['a,b'], 'movs.csv');
    const input = fakeInput(file);
    dialog.onFile(input);
    expect(dialog.file).toBe(file);
    expect(input.value).toBe('');
    expect(dialog.result).toBeNull();
    expect(dialog.error).toBe('');
  });

  it('onFile sin fichero seleccionado deja file a null', () => {
    const dialog = create();
    dialog.onFile(fakeInput(null));
    expect(dialog.file).toBeNull();
  });

  describe('doImport', () => {
    it('sin fichero, no hace nada', () => {
      const dialog = create();
      dialog.doImport();
      expect(api.importTransactions).not.toHaveBeenCalled();
    });

    it('para movimientos, importa a la cuenta seleccionada', () => {
      const dialog = create('transactions');
      const file = new File(['x'], 'x.csv');
      dialog.file = file;
      dialog.accountId = 1;
      dialog.doImport();
      expect(api.importTransactions).toHaveBeenCalledWith(file, 1);
    });

    it('para transferencias, importa sin cuenta', () => {
      const dialog = create('transfers');
      const file = new File(['x'], 'x.csv');
      dialog.file = file;
      dialog.doImport();
      expect(api.importTransfers).toHaveBeenCalledWith(file);
    });

    it('en éxito con filas importadas, emite done y limpia el fichero', () => {
      const dialog = create();
      const onDone = vi.fn();
      dialog.done.subscribe(onDone);
      dialog.file = new File(['x'], 'x.csv');
      dialog.doImport();
      expect(dialog.loading).toBe(false);
      expect(dialog.result).toEqual(okResult);
      expect(dialog.file).toBeNull();
      expect(onDone).toHaveBeenCalled();
    });

    it('en éxito sin filas importadas, no emite done', () => {
      const dialog = create();
      api.importTransactions.mockReturnValue(of({ imported: 0, duplicated: 0, errors: [] }));
      const onDone = vi.fn();
      dialog.done.subscribe(onDone);
      dialog.file = new File(['x'], 'x.csv');
      dialog.doImport();
      expect(onDone).not.toHaveBeenCalled();
    });

    it('en error, usa el detail/message de la API o el mensaje genérico', () => {
      const dialog = create();
      api.importTransactions.mockReturnValue(throwError(() => ({ error: { detail: 'boom' } })));
      dialog.file = new File(['x'], 'x.csv');
      dialog.doImport();
      expect(dialog.loading).toBe(false);
      expect(dialog.error).toBe('boom');
    });

    it('en error sin detail ni message, usa el mensaje genérico', () => {
      const dialog = create();
      api.importTransactions.mockReturnValue(throwError(() => ({ error: null })));
      dialog.file = new File(['x'], 'x.csv');
      dialog.doImport();
      expect(dialog.error).toBe('Error al importar el fichero.');
    });
  });
});
