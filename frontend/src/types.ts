export type View = 'splash' | 'intro' | 'conditions' | 'signup' | 'birth' | 'login' | 'home' | 'capture' | 'analyzing' | 'extracted' | 'results' | 'approval' | 'reservations' | 'account';
export type Profile = { id: string; email: string; name: string; birthDate: string; region: string; budget: number; equipment: string[]; notifications: boolean };
export type Conditions = { purpose: string; date: string; start: string; end: string; region: string; budget: number | null; equipment: string[]; ownedEquipment: string[]; notes: string[] };
export type Candidate = { id: string; name: string; provider: string; url: string; address: string; price: number | null; equipment: string[]; eligibility: string; availability: string; evidence: string; observedAt: string };
export type Approval = { candidateId: string; name: string; url: string; date: string; start: string; end: string; totalPrice: number; cancellation: string; evidence: string; fingerprint: string };
export type AgentTask = { id: string; state: string; conditions: Conditions; candidates: Candidate[]; selectedId?: string; approval?: Approval; question?: string; message: string; bookingReference?: string; evidence?: string; events: { at: string; message: string }[]; updatedAt: string };
export type SystemStatus = { model: string; ollamaReady: boolean; vision: boolean; message: string; sites: { name: string; url: string }[] };
