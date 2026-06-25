/** No-op: IDs are now auto-increment and safe for JavaScript Number */
export function fixIds<T>(obj: T): T { return obj }

export async function parseJson<T>(res: Response): Promise<T> {
  return res.json()
}
