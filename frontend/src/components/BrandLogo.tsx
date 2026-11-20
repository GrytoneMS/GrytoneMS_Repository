import './brand-logo.css'

/** Final source: team_on_3 1.png, preserved byte-for-byte in public/brand/team-on.png. */
export function BrandLogo() {
  return <img className="teamon-logo" src={`${import.meta.env.BASE_URL}brand/team-on.png`} alt="TEAM:ON" width={659} height={115} />
}
