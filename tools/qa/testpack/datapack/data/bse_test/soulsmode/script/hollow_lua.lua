-- QA: ai102000's attack table S2147483585 (enemy_ai.md s.9.2). f5004 / f5102 / f5018 are unidentified,
-- so their branches take the "else" side; combos request only their first step (how a combo advances is unconfirmed).
local thinks = 0
function on_think(self)
  local d = bse.distance()
  local a = bse.angle()
  local r = math.random(0, 20)
  thinks = thinks + 1
  if thinks % 20 == 1 then print("think", self, d, a, r) end
  if d < 3.5 then
    if a >= 110 and d > 0 and d < 3 then return bse.attack("a13") end
    if r > 0 and r < 10 then return bse.attack("a14") end
    if r > 0 and r < 15 then return bse.attack("a13") end
    return bse.attack("a50")
  end
  if r > 0 and r < 10 and d < 6 then return bse.attack("a51") end
  if r > 0 and r < 17 then return bse.attack("a51") end
  if r > 0 and r < 18 then return bse.attack("a16") end
  if r > 0 and r < 19 then return bse.attack("a15") end
  if r > 0 and r < 20 then return bse.attack("a17") end
  return bse.attack("a13")
end
