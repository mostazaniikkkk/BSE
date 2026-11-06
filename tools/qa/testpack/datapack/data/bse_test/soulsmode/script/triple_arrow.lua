-- Example spell script (docs/datapacks.md, "Scripts"): three Soul Arrow bullets in a fan instead of one.
local SPREAD = 0.3 -- radians between arrows

function on_cast()
  local c = bse.caster()
  local yaw = math.rad(c.yaw)
  for i = -1, 1 do
    local a = yaw + i * SPREAD
    bse.emit("31010000", c.x, c.y + 1.4, c.z, -math.sin(a), 0, math.cos(a))
  end
end

function on_hit(target, bullet)
  local t = bse.entity(target)
  print("hit", target, bullet, t.hp)
end
