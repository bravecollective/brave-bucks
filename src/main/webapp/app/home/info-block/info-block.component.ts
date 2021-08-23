import { Component, OnInit } from '@angular/core';
import {ConfigService, Principal} from "../../shared";
import {Http} from "@angular/http";
import {Region} from "../../entities/solar-system/solar-system.model";

@Component({
  selector: 'jhi-info-block',
  templateUrl: './info-block.component.html',
  styles: []
})
export class InfoBlockComponent implements OnInit {

    regions = Object.keys(Region).filter(k => typeof Region[k as any] === "number");
    systems = {};

    walletUrl: string;
    characterNames: string[];

    constructor(private configService: ConfigService, private principal: Principal, private http: Http) {
    }

    ngOnInit() {
        this.principal.identity().then((account) => {
            this.configService.getWalletUrl().subscribe((data) => this.walletUrl = data + "-" + account.id);
        });

        for (let region of this.regions) {
            this.http.get('/api/solar-systems/region/'+region.toUpperCase()).subscribe((data) => {
                const systems = data.json();
                if (systems.length > 0) {
                    this.systems[region] = systems;
                }
            });
        }
    }

    revokeCharacter(characterName: string) {
        this.http.delete('/api/characters/' + characterName).subscribe((data) => {
            this.http.get('/api/characters').subscribe((charNames) => {
                this.characterNames = charNames.json();
            });
        });
    }

    /**
     * @param type "pvp" or "adm"
     */
    regionWithSystems(type: string) {
        return Object.keys(this.systems).filter((key) => {
            for (const system of this.systems[key]) {
                if (type === 'pvp' && system.trackPvp) {
                    return true;
                } else if (type === 'adm' && system.trackRatting) {
                    return true;
                }
            }
            return false;
        });
    }

    getDotlanLink(region: string, isPvp: boolean, isRatting: boolean) {
        const systemNames = [];
        if (this.systems.hasOwnProperty(region)) {
            this.systems[region].forEach((s) => {
                if (isPvp && s.trackPvp || isRatting && s.trackRatting) {
                    systemNames.push(s.systemName);
                }
            });
        }
        let url = "http://evemaps.dotlan.net/map/" + region + '/' + systemNames.join();
        if (isRatting) {
            url += "#adm";
        }
        return url;
    }

}
