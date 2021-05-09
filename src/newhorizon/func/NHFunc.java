package newhorizon.func;

import arc.Core;
import arc.func.Boolf;
import arc.func.Intc2;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Geometry;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import newhorizon.block.special.JumpGate;
import newhorizon.vars.NHVars;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static arc.math.Angles.randLenVectors;
import static mindustry.Vars.*;
import static mindustry.core.World.toTile;

public class NHFunc{
    private static Tile tileParma;
    private static Floor floorParma;
    private static final Seq<Tile> tiles = new Seq<>();
    private static final IntSeq buildingIDSeq = new IntSeq();
    private static final int maxCompute = 32;
    public static final Effect debugEffect = new Effect(120f, 300f, e -> {
        if(!(e.data instanceof Seq))return;
        Seq<Rect> data = e.data();
        Draw.color(Pal.lancerLaser);
        Draw.z(Layer.flyingUnit + 2f);
        for(Rect r : data){
            r.getCenter(Tmp.v1);
            Fill.square(Tmp.v1.x, Tmp.v1.y, tilesize / 2f);
        }
    });
    private static final Vec2 point = new Vec2();
    
    public static long seedNet(){
        return (long)Groups.build.size() << 4 + Groups.all.size();
    }
    
    public static Unit teleportUnitNet(Unit before, float x, float y, float angle){
        if(!net.client()){
            before.set(x, y);
            before.rotation = angle;
            return before;
        }else{
            Team t = Team.all[before.team.id];
            UnitType type = before.type;
            Unit unit = type.create(t);
            unit.set(x, y);
            unit.spawnedByCore(before.spawnedByCore);
            unit.rotation = angle;
            unit.addItem(before.item(), before.stack.amount);
            unit.health(before.health);
            unit.ammo = before.ammo;
    
            if(before.controller() instanceof Player){
                Player player = (Player)before.controller();
                player.team(Team.derelict);
                if(!net.client()) unit.add();
        
                while(player.unit() != unit && !player.within(x, y, tilesize * 2f)){
                    player.unit(unit);
                }
                if(mobile && !Vars.headless && player == Vars.player) Core.camera.position.set(x, y);
                Time.run(1f, () -> player.team(t));
            }
    
            if(!net.client()) unit.add();
            before.remove();
            return unit;
        }
    }
    
    public static void square(int x, int y, int radius, Intc2 cons) {
        for(int dx = -radius; dx <= radius; ++dx) {
            for(int dy = -radius; dy <= radius; ++dy) {
                cons.get(dx + x, dy + y);
            }
        }
    }
    
    /**
     * @implNote Get all the {@link Tile} {@code tile} within a certain range at certain position.
     * @param x the abscissa of search center.
     * @param y the ordinate of search center.
     * @param range the search range.
     * @param bool {@link Boolf} {@code lambda} to determine whether the condition is true.
     * @return {@link Seq}{@code <Tile>} - which contains eligible {@link Tile} {@code tile}.
     */
    public static Seq<Tile> getAcceptableTiles(int x, int y, int range, Boolf<Tile> bool){
        Seq<Tile> tiles = new Seq<>(true, (int)(Mathf.pow(range, 2) * Mathf.pi), Tile.class);
        Geometry.circle(x, y, range, (x1, y1) -> {
            if((tileParma = world.tile(x1, y1)) != null && bool.get(tileParma)){
                tiles.add(world.tile(x1, y1));
            }
        });
        return tiles;
    }
    
    private static void clearTmp(){
        tileParma = null;
        floorParma = null;
        buildingIDSeq.clear();
        tiles.clear();
    }
    
    public static int getTeamIndex(Team team){return NHVars.allTeamSeq.indexOf(team);}
    
    @Contract(value = "!null, _ -> param1", pure = true)
    public static Color getColor(Color defaultColor, Team team){
        return defaultColor == null ? team.color : defaultColor;
    }
    
    //not support server
    public static void spawnUnit(UnitType type, Team team, int spawnNum, float x, float y){
        for(int spawned = 0; spawned < spawnNum; spawned++){
            Time.run(spawned * Time.delta, () -> {
                Unit unit = type.create(team);
                if(unit != null){
                    unit.set(x, y);
                    unit.add();
                }else Log.info("Unit == null");
            });
        }
    }
    
    @Contract(pure = true)
    public static float regSize(@NotNull UnitType type){
        return type.hitSize / tilesize / tilesize / 3.25f;
    }
    
    public static boolean spawnUnit(Teamc starter, float x, float y, float spawnRange, float spawnReloadTime, float spawnDelay, long seed, JumpGate.UnitSet set, Color spawnColor){
        UnitType type = set.type;
        clearTmp();
        Seq<Vec2> vectorSeq = new Seq<>();
        Seq<Tile> tSeq = new Seq<>(Tile.class);
        float angle, regSize = regSize(type);
        
        if(!type.flying){
            Rand r = new Rand(seed);
            tSeq.addAll(getAcceptableTiles(toTile(x), toTile(y), toTile(spawnRange),
                tile -> !tile.floor().isDeep() && !tile.cblock().solid && !tile.floor().solid && !tile.overlay().solid && !tile.block().solidifes)
            );
            for(int i = 0; i < set.callIns; i++){
                Tile[] positions = tSeq.shrink();
                if(positions.length < set.callIns)return false;
                vectorSeq.add(new Vec2().set(positions[r.nextInt(positions.length)]));
            }
        }else{
            randLenVectors(seed, set.callIns, spawnRange, (sx, sy) -> vectorSeq.add(new Vec2(sx, sy).add(x, y)));
        }
        
        angle = starter.angleTo(x, y);
        
        int i = 0;
        for (Vec2 s : vectorSeq) {
            if(!Units.canCreate(starter.team(), type))break;
            FContents.spawnUnitDrawer.create(starter, starter.team(), s.x, s.y, angle, 1f, 1f, 1f, new FContents.SpawnerData(set, spawnRange, spawnDelay, spawnColor)).lifetime(spawnReloadTime + i * spawnDelay);
            i++;
        }
        return true;
    }
}