import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import sql from 'mssql';
import type { ConnectionPool, Transaction } from 'mssql';

type SqlParameter = string | number | boolean | Date | null;
type SqlParameters = Record<string, SqlParameter>;

@Injectable()
export class DatabaseService implements OnModuleDestroy {
  private poolPromise?: Promise<ConnectionPool>;

  constructor(private readonly config: ConfigService) {}

  async query<T = Record<string, unknown>>(
    statement: string,
    parameters: SqlParameters = {},
    transaction?: Transaction,
  ): Promise<T[]> {
    const request = transaction
      ? new sql.Request(transaction)
      : new sql.Request(await this.getPool());
    for (const [name, value] of Object.entries(parameters)) {
      request.input(name, value);
    }
    const result = await request.query<T>(statement);
    return result.recordset ?? [];
  }

  async withTransaction<T>(
    work: (transaction: Transaction) => Promise<T>,
  ): Promise<T> {
    const transaction = new sql.Transaction(await this.getPool());
    await transaction.begin(sql.ISOLATION_LEVEL.SERIALIZABLE);
    try {
      const result = await work(transaction);
      await transaction.commit();
      return result;
    } catch (error) {
      await transaction.rollback();
      throw error;
    }
  }

  async onModuleDestroy(): Promise<void> {
    if (this.poolPromise) {
      const pool = await this.poolPromise;
      if (pool.connected) {
        await pool.close();
      }
    }
  }

  private getPool(): Promise<ConnectionPool> {
    const activeConnection = this.poolPromise;
    if (activeConnection) {
      return activeConnection;
    }
    const pool = new sql.ConnectionPool({
      server: this.config.getOrThrow<string>('DB_SERVER'),
      port: Number(this.config.get('DB_PORT') ?? 1433),
      user: this.config.getOrThrow<string>('DB_USER'),
      password: this.config.getOrThrow<string>('DB_PASSWORD'),
      database: this.config.getOrThrow<string>('DB_NAME'),
      options: {
        encrypt: this.config.get('DB_ENCRYPT') === 'true',
        trustServerCertificate:
          this.config.get('DB_TRUST_SERVER_CERTIFICATE') !== 'false',
      },
      pool: { max: 10, min: 0, idleTimeoutMillis: 30000 },
    });
    const connecting = pool.connect().then(
      (connected) => connected,
      (error: unknown) => {
        this.poolPromise = undefined;
        throw error;
      },
    );
    this.poolPromise = connecting;
    return connecting;
  }
}
