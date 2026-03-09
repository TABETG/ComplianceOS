import { useState, useRef, useEffect } from "react";

// ─── Design tokens (fidèles à complianceos.jsx) ───────────────────────────────
const C = {
  bg: "#F8F7F4",
  surface: "#FFFFFF",
  border: "#E8E4DC",
  border2: "#D4CFC4",
  text: "#1A1814",
  muted: "#6B6458",
  faint: "#9C9488",
  accent: "#2D6A4F",
  accentLight: "#E8F5EE",
  accentBorder: "#B7DEC9",
  orange: "#C4622D",
  orangeLight: "#FDF0E8",
  red: "#B83232",
  redLight: "#FAEAEA",
  blue: "#1E4D8C",
  blueLight: "#EAF0FA",
  yellow: "#8C6D1E",
  yellowLight: "#FBF5E0",
};

// ─── NIS2 sectors ─────────────────────────────────────────────────────────────
const SECTORS = [
  { value: "ENERGIE", label: "Énergie" },
  { value: "TRANSPORT", label: "Transport" },
  { value: "SANTE", label: "Santé" },
  { value: "FINANCE", label: "Finance & Services bancaires" },
  { value: "EAU", label: "Eau potable" },
  { value: "NUMERIQUE", label: "Infrastructures numériques" },
  { value: "ESPACE", label: "Espace" },
  { value: "ADMINISTRATION", label: "Administration publique" },
  { value: "AUTRE", label: "Autre secteur critique" },
];

// ─── Validation helpers (mirroring backend validators) ───────────────────────
const BLOCKED_DOMAINS = new Set([
  "gmail.com","googlemail.com","yahoo.com","yahoo.fr","hotmail.com",
  "hotmail.fr","live.com","outlook.com","wanadoo.fr","orange.fr",
  "free.fr","sfr.fr","laposte.net","icloud.com","me.com","mac.com",
  "aol.com","protonmail.com","proton.me","tutanota.com","yopmail.com",
  "mailinator.com","temp-mail.org","guerrillamail.com",
]);

function validateEmail(email) {
  if (!email) return "L'e-mail est requis";
  const emailRegex = /^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$/;
  if (!emailRegex.test(email)) return "Format d'e-mail invalide";
  const domain = email.split("@")[1]?.toLowerCase();
  if (BLOCKED_DOMAINS.has(domain)) return "Veuillez utiliser un e-mail professionnel";
  return null;
}

function luhnCheck(number) {
  let sum = 0, alternate = false;
  for (let i = number.length - 1; i >= 0; i--) {
    let digit = parseInt(number[i], 10);
    if (alternate) { digit *= 2; if (digit > 9) digit -= 9; }
    sum += digit;
    alternate = !alternate;
  }
  return sum % 10 === 0;
}

function validateSiret(siret) {
  if (!siret) return "Le SIRET est requis";
  const cleaned = siret.replace(/\s/g, "");
  if (!/^\d{14}$/.test(cleaned)) return "Le SIRET doit contenir exactement 14 chiffres";
  if (!luhnCheck(cleaned)) return "SIRET invalide (vérification Luhn échouée)";
  return null;
}

function validateForm(fields) {
  return {
    email: validateEmail(fields.email),
    companyName: !fields.companyName ? "Le nom de l'entreprise est requis"
      : fields.companyName.length < 2 ? "Minimum 2 caractères" : null,
    siret: validateSiret(fields.siret),
    sector: !fields.sector ? "Veuillez sélectionner un secteur NIS2" : null,
    acceptTerms: !fields.acceptTerms ? "Vous devez accepter les conditions" : null,
  };
}

// ─── Input Field ─────────────────────────────────────────────────────────────
function Field({ label, error, touched, hint, children }) {
  const hasError = touched && error;
  return (
    <div style={{ marginBottom: "20px" }}>
      <label style={{
        display: "block", fontSize: "11px", letterSpacing: "1.5px",
        textTransform: "uppercase", color: hasError ? C.red : C.muted,
        marginBottom: "6px", fontFamily: "system-ui", fontWeight: "600",
        transition: "color 0.2s",
      }}>
        {label}
      </label>
      {children}
      {hasError && (
        <div style={{
          marginTop: "5px", fontSize: "12px", color: C.red,
          fontFamily: "system-ui", display: "flex", gap: "5px", alignItems: "center",
        }}>
          <span>⚠</span> {error}
        </div>
      )}
      {hint && !hasError && (
        <div style={{ marginTop: "5px", fontSize: "11px", color: C.faint, fontFamily: "system-ui" }}>
          {hint}
        </div>
      )}
    </div>
  );
}

const inputStyle = (hasError) => ({
  width: "100%", boxSizing: "border-box",
  padding: "10px 14px", fontSize: "14px",
  fontFamily: "'Palatino Linotype', Palatino, serif",
  background: C.surface, color: C.text,
  border: `1px solid ${hasError ? C.red : C.border}`,
  borderRadius: "4px", outline: "none",
  transition: "border-color 0.2s, box-shadow 0.2s",
});

// ─── Step indicator ───────────────────────────────────────────────────────────
function StepDot({ n, active, done }) {
  return (
    <div style={{
      width: "28px", height: "28px", borderRadius: "50%", fontSize: "11px",
      fontFamily: "system-ui", fontWeight: "700",
      display: "flex", alignItems: "center", justifyContent: "center",
      background: done ? C.accent : active ? C.accentLight : C.bg,
      color: done ? "#fff" : active ? C.accent : C.faint,
      border: `1px solid ${done ? C.accent : active ? C.accentBorder : C.border}`,
      transition: "all 0.3s",
      flexShrink: 0,
    }}>
      {done ? "✓" : n}
    </div>
  );
}

// ─── Success Screen ───────────────────────────────────────────────────────────
function SuccessScreen({ email }) {
  return (
    <div style={{
      textAlign: "center", padding: "48px 24px",
      animation: "fadeIn 0.5s ease",
    }}>
      <div style={{
        width: "64px", height: "64px", borderRadius: "50%",
        background: C.accentLight, border: `2px solid ${C.accentBorder}`,
        display: "flex", alignItems: "center", justifyContent: "center",
        margin: "0 auto 24px", fontSize: "28px",
      }}>✓</div>
      <h2 style={{
        fontSize: "26px", fontWeight: "400", color: C.text,
        letterSpacing: "-0.5px", margin: "0 0 12px",
        fontFamily: "'Palatino Linotype', Palatino, serif",
      }}>Compte créé avec succès</h2>
      <p style={{
        fontSize: "14px", color: C.muted, lineHeight: 1.7,
        maxWidth: "360px", margin: "0 auto 28px",
        fontFamily: "system-ui",
      }}>
        Un e-mail de confirmation a été envoyé à <strong style={{ color: C.text }}>{email}</strong>.
        Le lien d'activation est valable <strong style={{ color: C.text }}>24 heures</strong>.
      </p>
      <div style={{
        background: C.accentLight, border: `1px solid ${C.accentBorder}`,
        borderRadius: "6px", padding: "14px 20px", maxWidth: "360px",
        margin: "0 auto", textAlign: "left",
        fontFamily: "system-ui", fontSize: "13px", color: C.muted,
      }}>
        <div style={{ marginBottom: "8px", fontWeight: "600", color: C.accent, fontSize: "11px", letterSpacing: "1.5px", textTransform: "uppercase" }}>Étapes suivantes</div>
        {["Vérifiez votre boîte e-mail", "Cliquez sur le lien d'activation", "Définissez votre Passkey FIDO2", "Accédez à votre tableau de bord"].map((step, i) => (
          <div key={i} style={{ display: "flex", gap: "10px", marginBottom: "6px", alignItems: "flex-start" }}>
            <span style={{ color: C.accent, fontWeight: "700", flexShrink: 0 }}>{i + 1}.</span>
            <span>{step}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────
export default function RegistrationForm() {
  const [fields, setFields] = useState({
    email: "", companyName: "", siret: "", sector: "", phone: "", acceptTerms: false,
  });
  const [touched, setTouched] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [serverError, setServerError] = useState(null);
  const [step, setStep] = useState(1); // 1 = company info, 2 = contact

  const errors = validateForm(fields);
  const isStep1Valid = !errors.companyName && !errors.siret && !errors.sector && fields.companyName && fields.siret && fields.sector;
  const isFormValid = Object.values(errors).every(e => !e);

  const set = (key) => (e) => {
    const val = e.target.type === "checkbox" ? e.target.checked : e.target.value;
    setFields(f => ({ ...f, [key]: val }));
  };

  const blur = (key) => () => setTouched(t => ({ ...t, [key]: true }));

  const goToStep2 = () => {
    setTouched(t => ({ ...t, companyName: true, siret: true, sector: true }));
    if (isStep1Valid) setStep(2);
  };

  const handleSubmit = async () => {
    setTouched({ email: true, companyName: true, siret: true, sector: true, acceptTerms: true });
    if (!isFormValid) return;

    setSubmitting(true);
    setServerError(null);

    // Simulate API call → POST /api/v1/registration
    await new Promise(r => setTimeout(r, 1400));

    // Simulate success (random failure for demo)
    if (Math.random() > 0.1) {
      setSubmitted(true);
    } else {
      setServerError("Une erreur est survenue. Veuillez réessayer dans quelques instants.");
    }
    setSubmitting(false);
  };

  if (submitted) return (
    <div style={{ minHeight: "100vh", background: C.bg, display: "flex", alignItems: "center", justifyContent: "center", padding: "20px" }}>
      <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: "8px", width: "100%", maxWidth: "520px" }}>
        <div style={{ padding: "28px 32px", borderBottom: `1px solid ${C.border}` }}>
          <LogoHeader />
        </div>
        <SuccessScreen email={fields.email} />
      </div>
    </div>
  );

  return (
    <div style={{ minHeight: "100vh", background: C.bg, display: "flex", alignItems: "flex-start", justifyContent: "center", padding: "40px 20px", fontFamily: "'Palatino Linotype', Palatino, serif" }}>
      <div style={{ width: "100%", maxWidth: "520px" }}>

        {/* Card */}
        <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: "8px", overflow: "hidden" }}>

          {/* Header */}
          <div style={{ padding: "28px 32px", borderBottom: `1px solid ${C.border}` }}>
            <LogoHeader />
            <p style={{ margin: "10px 0 0", fontSize: "13px", color: C.muted, fontFamily: "system-ui", lineHeight: 1.6 }}>
              Créez l'espace de conformité de votre entreprise. Votre tenant isolé sera provisionné automatiquement.
            </p>
          </div>

          {/* Step indicator */}
          <div style={{
            padding: "16px 32px", borderBottom: `1px solid ${C.border}`,
            background: C.bg, display: "flex", alignItems: "center", gap: "0",
          }}>
            {[
              { n: 1, label: "Entreprise" },
              { n: 2, label: "Contact & confirmation" },
            ].map(({ n, label }, i) => (
              <div key={n} style={{ display: "flex", alignItems: "center", flex: i === 0 ? "none" : "1" }}>
                <StepDot n={n} active={step === n} done={step > n} />
                <span style={{
                  marginLeft: "8px", fontSize: "12px", color: step === n ? C.accent : C.faint,
                  fontFamily: "system-ui", fontWeight: step === n ? "600" : "400",
                }}>{label}</span>
                {i === 0 && (
                  <div style={{ flex: 1, height: "1px", background: step > 1 ? C.accent : C.border, margin: "0 12px", transition: "background 0.3s" }} />
                )}
              </div>
            ))}
          </div>

          {/* Form body */}
          <div style={{ padding: "28px 32px" }}>

            {/* ── Step 1 : Informations entreprise ─────────────── */}
            {step === 1 && (
              <div>
                <Field label="Nom de l'entreprise" error={errors.companyName} touched={touched.companyName}>
                  <input
                    value={fields.companyName}
                    onChange={set("companyName")}
                    onBlur={blur("companyName")}
                    placeholder="ACME Corporation SAS"
                    style={inputStyle(touched.companyName && errors.companyName)}
                  />
                </Field>

                <Field
                  label="SIRET"
                  error={errors.siret}
                  touched={touched.siret}
                  hint="14 chiffres — identifiant INSEE de l'établissement"
                >
                  <input
                    value={fields.siret}
                    onChange={(e) => {
                      const v = e.target.value.replace(/\D/g, "").slice(0, 14);
                      setFields(f => ({ ...f, siret: v }));
                    }}
                    onBlur={blur("siret")}
                    placeholder="73282932000074"
                    maxLength={14}
                    style={{
                      ...inputStyle(touched.siret && errors.siret),
                      fontFamily: "'Courier New', monospace",
                      letterSpacing: "1px",
                    }}
                  />
                  {fields.siret.length > 0 && (
                    <div style={{ marginTop: "5px", display: "flex", gap: "4px" }}>
                      {Array.from({ length: 14 }, (_, i) => (
                        <div key={i} style={{
                          flex: 1, height: "3px", borderRadius: "2px",
                          background: i < fields.siret.length
                            ? (touched.siret && errors.siret ? C.red : C.accent)
                            : C.border,
                          transition: "background 0.2s",
                        }} />
                      ))}
                    </div>
                  )}
                </Field>

                <Field label="Secteur NIS2" error={errors.sector} touched={touched.sector} hint="Secteur selon la directive NIS2 (art. 3)">
                  <select
                    value={fields.sector}
                    onChange={set("sector")}
                    onBlur={blur("sector")}
                    style={{
                      ...inputStyle(touched.sector && errors.sector),
                      appearance: "none",
                      backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath d='M1 1l5 5 5-5' stroke='%236B6458' fill='none' stroke-width='1.5'/%3E%3C/svg%3E")`,
                      backgroundRepeat: "no-repeat",
                      backgroundPosition: "right 14px center",
                      paddingRight: "36px",
                      cursor: "pointer",
                    }}
                  >
                    <option value="">Sélectionner un secteur…</option>
                    {SECTORS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
                  </select>
                </Field>

                <button
                  onClick={goToStep2}
                  style={{
                    width: "100%", padding: "12px", marginTop: "4px",
                    background: isStep1Valid ? C.accent : C.border,
                    color: isStep1Valid ? "#fff" : C.faint,
                    border: "none", borderRadius: "4px",
                    fontSize: "14px", fontFamily: "system-ui", fontWeight: "600",
                    cursor: isStep1Valid ? "pointer" : "default",
                    letterSpacing: "0.3px", transition: "all 0.2s",
                  }}
                >
                  Continuer →
                </button>
              </div>
            )}

            {/* ── Step 2 : Contact & confirmation ──────────────── */}
            {step === 2 && (
              <div>
                <button onClick={() => setStep(1)} style={{
                  background: "none", border: "none", cursor: "pointer",
                  color: C.muted, fontSize: "13px", fontFamily: "system-ui",
                  padding: "0", marginBottom: "20px", display: "flex", gap: "6px", alignItems: "center",
                }}>
                  ← Retour
                </button>

                <Field
                  label="E-mail professionnel"
                  error={errors.email}
                  touched={touched.email}
                  hint="Cet e-mail sera votre identifiant administrateur"
                >
                  <input
                    type="email"
                    value={fields.email}
                    onChange={set("email")}
                    onBlur={blur("email")}
                    placeholder="admin@votre-entreprise.fr"
                    style={inputStyle(touched.email && errors.email)}
                  />
                </Field>

                <Field label="Téléphone (optionnel)">
                  <input
                    type="tel"
                    value={fields.phone}
                    onChange={set("phone")}
                    placeholder="+33 1 23 45 67 89"
                    style={inputStyle(false)}
                  />
                </Field>

                {/* Tenant preview */}
                <div style={{
                  background: C.bg, border: `1px solid ${C.border}`,
                  borderRadius: "6px", padding: "14px 16px",
                  marginBottom: "20px", fontFamily: "system-ui",
                }}>
                  <div style={{ fontSize: "10px", letterSpacing: "2px", textTransform: "uppercase", color: C.faint, marginBottom: "10px" }}>
                    Votre espace sera configuré avec
                  </div>
                  {[
                    ["Tenant PostgreSQL isolé", "Row-Level Security activé"],
                    ["Chiffrement at-rest", "AES-256 via AWS KMS"],
                    ["Compte Keycloak", "Rôle ADMIN · Passkeys FIDO2"],
                    ["Plan d'essai", "3 audits / mois · 14 jours"],
                  ].map(([k, v]) => (
                    <div key={k} style={{
                      display: "flex", justifyContent: "space-between",
                      fontSize: "12px", marginBottom: "6px",
                    }}>
                      <span style={{ color: C.muted }}>{k}</span>
                      <span style={{ color: C.accent, fontWeight: "500" }}>{v}</span>
                    </div>
                  ))}
                </div>

                {/* Terms */}
                <label style={{
                  display: "flex", gap: "10px", alignItems: "flex-start",
                  cursor: "pointer", marginBottom: "24px",
                  padding: "12px", borderRadius: "4px",
                  background: touched.acceptTerms && errors.acceptTerms ? C.redLight : "transparent",
                  border: `1px solid ${touched.acceptTerms && errors.acceptTerms ? C.red : "transparent"}`,
                  transition: "all 0.2s",
                }}>
                  <input
                    type="checkbox"
                    checked={fields.acceptTerms}
                    onChange={set("acceptTerms")}
                    onBlur={blur("acceptTerms")}
                    style={{ marginTop: "2px", accentColor: C.accent }}
                  />
                  <span style={{ fontSize: "12px", color: C.muted, fontFamily: "system-ui", lineHeight: 1.6 }}>
                    J'accepte les{" "}
                    <span style={{ color: C.accent, textDecoration: "underline", cursor: "pointer" }}>conditions d'utilisation</span>{" "}
                    et la{" "}
                    <span style={{ color: C.accent, textDecoration: "underline", cursor: "pointer" }}>politique de confidentialité</span>{" "}
                    de ComplianceOS (RGPD-compliant).
                  </span>
                </label>

                {serverError && (
                  <div style={{
                    background: C.redLight, border: `1px solid ${C.red}`,
                    borderRadius: "4px", padding: "10px 14px",
                    marginBottom: "16px", fontSize: "13px", color: C.red,
                    fontFamily: "system-ui",
                  }}>
                    {serverError}
                  </div>
                )}

                <button
                  onClick={handleSubmit}
                  disabled={submitting}
                  style={{
                    width: "100%", padding: "13px",
                    background: submitting ? C.accentBorder : C.accent,
                    color: "#fff", border: "none", borderRadius: "4px",
                    fontSize: "14px", fontFamily: "system-ui", fontWeight: "600",
                    cursor: submitting ? "not-allowed" : "pointer",
                    letterSpacing: "0.3px", display: "flex", alignItems: "center",
                    justifyContent: "center", gap: "10px", transition: "background 0.2s",
                  }}
                >
                  {submitting ? (
                    <>
                      <Spinner />
                      Création du tenant en cours…
                    </>
                  ) : "Créer mon compte entreprise"}
                </button>
              </div>
            )}
          </div>

          {/* Footer */}
          <div style={{
            padding: "14px 32px", borderTop: `1px solid ${C.border}`,
            background: C.bg, display: "flex", justifyContent: "space-between",
            alignItems: "center",
          }}>
            <span style={{ fontSize: "11px", color: C.faint, fontFamily: "system-ui" }}>
              Déjà un compte ?{" "}
              <span style={{ color: C.accent, cursor: "pointer", textDecoration: "underline" }}>Se connecter</span>
            </span>
            <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
              <span style={{ fontSize: "10px", color: C.faint, fontFamily: "system-ui" }}>Certifié</span>
              {["NIS2", "DORA", "eIDAS 2.0"].map(tag => (
                <span key={tag} style={{
                  background: C.accentLight, border: `1px solid ${C.accentBorder}`,
                  borderRadius: "2px", padding: "1px 6px",
                  fontSize: "9px", color: C.accent, fontFamily: "system-ui", fontWeight: "700",
                  letterSpacing: "0.5px",
                }}>{tag}</span>
              ))}
            </div>
          </div>
        </div>

        {/* Below card trust signals */}
        <div style={{
          marginTop: "16px", display: "flex", gap: "20px", justifyContent: "center",
          flexWrap: "wrap",
        }}>
          {[
            { icon: "🔒", text: "Chiffrement AES-256" },
            { icon: "🇪🇺", text: "Données hébergées EU" },
            { icon: "⚡", text: "Tenant provisionné < 2s" },
          ].map(({ icon, text }) => (
            <div key={text} style={{
              display: "flex", gap: "5px", alignItems: "center",
              fontSize: "11px", color: C.faint, fontFamily: "system-ui",
            }}>
              <span>{icon}</span> {text}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function LogoHeader() {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
      <div style={{
        width: "36px", height: "36px", borderRadius: "6px",
        background: C.accent, display: "flex", alignItems: "center", justifyContent: "center",
      }}>
        <span style={{ color: "#fff", fontSize: "16px", fontWeight: "700" }}>C</span>
      </div>
      <div>
        <div style={{ fontSize: "17px", fontWeight: "600", color: C.text, letterSpacing: "-0.3px" }}>
          Compliance<span style={{ color: C.accent }}>OS</span>
        </div>
        <div style={{ fontSize: "10px", color: C.faint, letterSpacing: "1.5px", textTransform: "uppercase", fontFamily: "system-ui" }}>
          Inscription entreprise
        </div>
      </div>
    </div>
  );
}

function Spinner() {
  return (
    <div style={{
      width: "14px", height: "14px", border: "2px solid rgba(255,255,255,0.3)",
      borderTopColor: "#fff", borderRadius: "50%",
      animation: "spin 0.7s linear infinite",
    }}>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } } @keyframes fadeIn { from { opacity: 0; transform: translateY(10px) } to { opacity: 1; transform: translateY(0) } }`}</style>
    </div>
  );
}
